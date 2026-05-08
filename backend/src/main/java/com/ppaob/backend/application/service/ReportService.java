package com.ppaob.backend.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppaob.backend.application.port.out.AnalysisRepositoryPort;
import com.ppaob.backend.application.port.out.ArtifactRepositoryPort;
import com.ppaob.backend.application.port.out.ObjectStoragePort;
import com.ppaob.backend.domain.model.ArtifactRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

@Service
/**
 * Generates and serves HTML analysis reports.
 *
 * <p>The service validates requester visibility over analysis/result data, renders a defensive
 * pre-exploitation report view, stores generated HTML artifacts in object storage, persists
 * artifact metadata, and emits best-effort audit events.
 */
public class ReportService {

    private static final String HTML_REPORT = "HTML";

    private final AnalysisRepositoryPort analyses;
    private final ArtifactRepositoryPort artifacts;
    private final ObjectStoragePort storage;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final String bucket;

/**
 * Creates the report service.
 *
 * @param analyses analysis repository for visibility-aware analysis/result reads
 * @param artifacts artifact repository for report metadata persistence and lookup
 * @param storage object storage adapter for report payload persistence and download
 * @param objectMapper JSON mapper for analysis result parsing
 * @param auditService audit logger used in best-effort mode
 * @param bucket storage bucket used for generated reports
 */
    public ReportService(
            AnalysisRepositoryPort analyses,
            ArtifactRepositoryPort artifacts,
            ObjectStoragePort storage,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Value("${app.storage.s3.bucket:ppaob-binaries}") String bucket
    ) {
        this.analyses = analyses;
        this.artifacts = artifacts;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.bucket = bucket;
    }

    @Transactional
/**
 * Generates an HTML report artifact for an analysis visible to the requester.
 *
 * <p>The method parses the stored result JSON, renders a deterministic HTML view with ethical
 * scope disclaimer, uploads it to storage, and creates a corresponding artifact row.
 *
 * <p>Side effects: writes report object to storage, inserts artifact metadata row, emits
 * best-effort {@code REPORT_GENERATE/SUCCESS} audit event.
 *
 * @param analysisId analysis identifier
 * @param requesterId requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return created artifact metadata for the generated report
 * @throws IllegalArgumentException when analysis/result is not visible/found or result JSON is invalid
 */
    public ArtifactRecord generateHtmlReport(UUID analysisId, UUID requesterId, boolean requesterIsAdmin) {
        var analysis = analyses.findById(analysisId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found"));
        var result = analyses.findResultByAnalysisId(analysisId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Analysis result not found"));

        JsonNode resultJson;
        try {
            resultJson = objectMapper.readTree(result.resultsJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid analysis result payload");
        }

        String html = renderHtml(analysis.analysisId(), analysis.binaryId(), resultJson, result.storedAt());
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);
        String checksum = sha256Hex(payload);
        String objectKey = "reports/" + analysis.analysisId() + "/" + UUID.randomUUID() + ".html";

        storage.upload(bucket, objectKey, payload, "text/html; charset=utf-8");
        ArtifactRecord artifact = artifacts.create(analysis.analysisId(), HTML_REPORT, bucket, objectKey, checksum, payload.length);

        safeAudit(
                "REPORT_GENERATE",
                "SUCCESS",
                requesterId,
                analysis.analysisId(),
                analysis.binaryId(),
                auditService.details(
                        "artifactId", artifact.artifactId(),
                        "type", artifact.type(),
                        "sizeBytes", artifact.sizeBytes()
                )
        );

        return artifact;
    }

/**
 * Lists HTML report artifacts visible to the requester.
 *
 * <p>The method fetches up to 200 visible artifacts and keeps only type {@code HTML}; returned
 * result size is capped by sanitized limit in {@code [1, 200]}.
 *
 * @param requesterId requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @param limit requested maximum number of rows
 * @return visible HTML report artifacts
 */
    public List<ArtifactRecord> listReports(UUID requesterId, boolean requesterIsAdmin, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return artifacts.listForRequester(requesterId, requesterIsAdmin, 200)
                .stream()
                .filter(artifact -> HTML_REPORT.equalsIgnoreCase(artifact.type()))
                .limit(safeLimit)
                .toList();
    }

/**
 * Downloads a previously generated report visible to the requester.
 *
 * <p>Side effects: reads report bytes from object storage and emits best-effort
 * {@code REPORT_DOWNLOAD/SUCCESS} audit event.
 *
 * @param artifactId report artifact identifier
 * @param requesterId requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return report metadata and payload bytes with resolved filename
 * @throws IllegalArgumentException when report is not visible/found or storage object is missing
 */
    public DownloadedReport downloadReport(UUID artifactId, UUID requesterId, boolean requesterIsAdmin) {
        ArtifactRecord artifact = artifacts.findByIdForRequester(artifactId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        if (!storage.exists(artifact.bucket(), artifact.objectKey())) {
            throw new IllegalArgumentException("Report object is not available in storage");
        }

        byte[] content = storage.download(artifact.bucket(), artifact.objectKey());

        safeAudit(
                "REPORT_DOWNLOAD",
                "SUCCESS",
                requesterId,
                artifact.analysisId(),
                null,
                auditService.details(
                        "artifactId", artifact.artifactId(),
                        "type", artifact.type()
                )
        );

        return new DownloadedReport(artifact, content, resolveFileName(artifact));
    }

/**
 * Resolves download filename for a report artifact.
 *
 * @param artifact report artifact metadata
 * @return filename with analysis id and UTC timestamp
 */
    public String resolveFileName(ArtifactRecord artifact) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(artifact.createdAt());
        return "analysis-" + artifact.analysisId() + "-" + stamp + ".html";
    }

    private String renderHtml(UUID analysisId, UUID binaryId, JsonNode resultJson, Instant storedAt) {
        String risk = escape(readText(resultJson, "UNKNOWN", "/summary/riskLevel"));
        int findingsCount = readInt(resultJson, 0, "/summary/findingsCount");
        int riskScore = readInt(resultJson, 0, "/summary/riskScore");
        String profile = escape(readText(resultJson, "UNKNOWN", "/metadata/requestedProfile", "/profile"));
        String priority = escape(readText(resultJson, "UNKNOWN", "/correlation/priority"));
        String environmentProfile = escape(readText(resultJson, "UNKNOWN", "/correlation/environmentProfile"));
        String generatedAt = escape(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        String sourceStoredAt = escape(DateTimeFormatter.ISO_INSTANT.format(storedAt));
        boolean hasStatic = hasObject(resultJson, "/static") || hasObject(resultJson, "/elfInfo");
        boolean hasDynamic = hasObject(resultJson, "/dynamic") || hasObject(resultJson, "/runtime");

        JsonNode staticNode = firstPresent(resultJson, "/static", "/elfInfo");
        JsonNode dynamicNode = firstPresent(resultJson, "/dynamic");
        JsonNode legacyRuntime = firstPresent(resultJson, "/runtime");
        JsonNode legacyPolicy = firstPresent(resultJson, "/policy");
        JsonNode legacyTopSyscalls = firstPresent(resultJson, "/topSyscalls");

        String topReasons = renderTopReasons(resultJson.path("correlation").path("topReasons"));
        String findingsRows = renderFindings(resultJson.path("signals"));
        String artifactRows = renderArtifacts(resultJson.path("artifacts"));
        String staticSummaryRows = renderStaticSummary(staticNode, resultJson.path("elfInfo"));
        String dynamicSummaryRows = renderDynamicSummary(dynamicNode, legacyRuntime, legacyPolicy, legacyTopSyscalls);

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>PPAOB Report</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 24px; color: #1f2937; }
                    h1 { margin-bottom: 8px; }
                    h2 { margin-top: 24px; }
                    .meta { color: #4b5563; margin-bottom: 16px; }
                    .cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
                    .card { border: 1px solid #d1d5db; border-radius: 8px; padding: 10px 12px; min-width: 180px; }
                    table { width: 100%%; border-collapse: collapse; }
                    th, td { border: 1px solid #d1d5db; padding: 8px; text-align: left; }
                    th { background: #f3f4f6; }
                    .disclaimer { margin-top: 24px; padding: 12px; border-radius: 8px; background: #fef3c7; border: 1px solid #f59e0b; color: #78350f; }
                    .kpi { font-size: 20px; font-weight: 700; }
                  </style>
                </head>
                <body>
                  <h1>PPAOB Analysis Report</h1>
                  <p class="meta">Generated at %s UTC</p>
                  <div class="cards">
                    <div class="card"><strong>Analysis ID</strong><div>%s</div></div>
                    <div class="card"><strong>Binary ID</strong><div>%s</div></div>
                    <div class="card"><strong>Risk Level</strong><div>%s</div></div>
                    <div class="card"><strong>Priority</strong><div class="kpi">%s</div></div>
                    <div class="card"><strong>Risk Score</strong><div>%d/100</div></div>
                    <div class="card"><strong>Findings</strong><div>%d</div></div>
                    <div class="card"><strong>Profile</strong><div>%s</div></div>
                    <div class="card"><strong>Environment</strong><div>%s</div></div>
                    <div class="card"><strong>Result Stored At</strong><div>%s</div></div>
                  </div>
                  <h2>Executive Summary</h2>
                  <ul>
                    <li>Static evidence available: %s</li>
                    <li>Dynamic evidence available: %s</li>
                    <li>Top reasons: %s</li>
                  </ul>
                  <h2>Static Summary</h2>
                  <table>
                    <thead>
                      <tr><th>Field</th><th>Value</th></tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                  <h2>Dynamic Summary</h2>
                  <table>
                    <thead>
                      <tr><th>Field</th><th>Value</th></tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                  <h2>Findings / Signals</h2>
                  <table>
                    <thead>
                      <tr><th>ID</th><th>Kind</th><th>Severity</th><th>Title</th></tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                  <h2>Artifacts</h2>
                  <table>
                    <thead>
                      <tr><th>Artifact ID</th><th>Type</th><th>Bucket</th><th>Object Key</th><th>Size</th></tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                  <div class="disclaimer">
                    <strong>Ethical Scope Disclaimer:</strong> This report is generated for controlled, defensive pre-exploitation analysis of binaries. It does not include automated exploitation, payload generation, persistence, or post-exploitation instructions.
                  </div>
                </body>
                </html>
                """.formatted(
                generatedAt,
                escape(analysisId.toString()),
                escape(binaryId.toString()),
                risk,
                priority,
                riskScore,
                findingsCount,
                profile,
                environmentProfile,
                sourceStoredAt,
                hasStatic ? "yes" : "no",
                hasDynamic ? "yes" : "no",
                topReasons,
                staticSummaryRows,
                dynamicSummaryRows,
                findingsRows,
                artifactRows
        );
    }

    private String renderFindings(JsonNode signals) {
        StringBuilder findingsRows = new StringBuilder();
        if (signals.isArray() && !signals.isEmpty()) {
            for (JsonNode signal : signals) {
                findingsRows.append("<tr>")
                        .append("<td>").append(escape(signal.path("id").asText("-"))).append("</td>")
                        .append("<td>").append(escape(signal.path("kind").asText("-"))).append("</td>")
                        .append("<td>").append(escape(signal.path("severity").asText("-"))).append("</td>")
                        .append("<td>").append(escape(signal.path("title").asText("-"))).append("</td>")
                        .append("</tr>");
            }
            return findingsRows.toString();
        }
        return "<tr><td colspan=\"4\">No findings detected</td></tr>";
    }

    private String renderArtifacts(JsonNode artifacts) {
        if (!artifacts.isArray() || artifacts.isEmpty()) {
            return "<tr><td colspan=\"5\">No artifacts linked</td></tr>";
        }
        StringBuilder rows = new StringBuilder();
        for (JsonNode artifact : artifacts) {
            rows.append("<tr>")
                    .append("<td>").append(escape(artifact.path("artifactId").asText("-"))).append("</td>")
                    .append("<td>").append(escape(artifact.path("type").asText("-"))).append("</td>")
                    .append("<td>").append(escape(artifact.path("bucket").asText("-"))).append("</td>")
                    .append("<td>").append(escape(artifact.path("objectKey").asText("-"))).append("</td>")
                    .append("<td>").append(escape(artifact.path("sizeBytes").asText("-"))).append("</td>")
                    .append("</tr>");
        }
        return rows.toString();
    }

    private String renderTopReasons(JsonNode topReasons) {
        if (!topReasons.isArray() || topReasons.isEmpty()) {
            return "none";
        }
        StringJoiner joiner = new StringJoiner(" | ");
        for (JsonNode reason : topReasons) {
            joiner.add(escape(reason.asText("-")));
        }
        return joiner.toString();
    }

    private String renderStaticSummary(JsonNode staticNode, JsonNode legacyElfInfoNode) {
        JsonNode elfNode = staticNode.path("elf");
        if ((elfNode == null || elfNode.isMissingNode() || elfNode.isNull() || elfNode.size() == 0)
                && legacyElfInfoNode != null && legacyElfInfoNode.isObject()) {
            elfNode = legacyElfInfoNode;
        }
        if (elfNode == null || !elfNode.isObject() || elfNode.size() == 0) {
            return "<tr><td colspan=\"2\">No static summary available</td></tr>";
        }
        return "<tr><td>isElf</td><td>%s</td></tr><tr><td>class</td><td>%s</td></tr><tr><td>architecture</td><td>%s</td></tr><tr><td>entrypoint</td><td>%s</td></tr><tr><td>elfType</td><td>%s</td></tr>"
                .formatted(
                        escape(elfNode.path("isElf").asText("-")),
                        escape(elfNode.path("class").asText("-")),
                        escape(elfNode.path("architecture").asText("-")),
                        escape(elfNode.path("entrypoint").asText("-")),
                        escape(elfNode.path("elfType").asText("-"))
                );
    }

    private String renderDynamicSummary(
            JsonNode dynamicNode,
            JsonNode legacyRuntimeNode,
            JsonNode legacyPolicyNode,
            JsonNode legacyTopSyscallsNode
    ) {
        JsonNode runtimeNode = dynamicNode.path("runtime");
        if ((runtimeNode == null || runtimeNode.isMissingNode() || runtimeNode.size() == 0)
                && legacyRuntimeNode != null && legacyRuntimeNode.isObject()) {
            runtimeNode = legacyRuntimeNode;
        }
        JsonNode policyNode = dynamicNode.path("policy");
        if ((policyNode == null || policyNode.isMissingNode() || policyNode.size() == 0)
                && legacyPolicyNode != null && legacyPolicyNode.isObject()) {
            policyNode = legacyPolicyNode;
        }
        JsonNode topSyscallsNode = dynamicNode.path("topSyscalls");
        if ((topSyscallsNode == null || topSyscallsNode.isMissingNode() || topSyscallsNode.size() == 0)
                && legacyTopSyscallsNode != null && legacyTopSyscallsNode.isArray()) {
            topSyscallsNode = legacyTopSyscallsNode;
        }

        if ((!runtimeNode.isObject() || runtimeNode.size() == 0)
                && (!policyNode.isObject() || policyNode.size() == 0)
                && (!topSyscallsNode.isArray() || topSyscallsNode.isEmpty())) {
            return "<tr><td colspan=\"2\">No dynamic summary available</td></tr>";
        }

        String topSyscall = "none";
        if (topSyscallsNode.isArray() && !topSyscallsNode.isEmpty()) {
            topSyscall = escape(topSyscallsNode.get(0).path("name").asText("none"));
        }

        return "<tr><td>durationMs</td><td>%s</td></tr><tr><td>exitCode</td><td>%s</td></tr><tr><td>timedOut</td><td>%s</td></tr><tr><td>policy</td><td>%s</td></tr><tr><td>deniedCount</td><td>%s</td></tr><tr><td>topSyscall</td><td>%s</td></tr>"
                .formatted(
                        escape(runtimeNode.path("durationMs").asText("-")),
                        escape(runtimeNode.path("exitCode").asText("-")),
                        escape(runtimeNode.path("timedOut").asText("-")),
                        escape(policyNode.path("name").asText("-")),
                        escape(policyNode.path("deniedCount").asText("-")),
                        topSyscall
                );
    }

    private JsonNode firstPresent(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode candidate = root.at(path);
            if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return objectMapper.createObjectNode();
    }

    private String readText(JsonNode root, String fallback, String... paths) {
        for (String path : paths) {
            JsonNode value = root.at(path);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return fallback;
    }

    private int readInt(JsonNode root, int fallback, String... paths) {
        for (String path : paths) {
            JsonNode value = root.at(path);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value.asInt(fallback);
            }
        }
        return fallback;
    }

    private boolean hasObject(JsonNode root, String path) {
        JsonNode value = root.at(path);
        return value != null && !value.isMissingNode() && value.isObject() && value.size() > 0;
    }

    private String escape(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void safeAudit(String action, String result, UUID userId, UUID analysisId, UUID binaryId, Map<String, Object> details) {
        try {
            auditService.log(action, result, userId, analysisId, binaryId, details);
        } catch (RuntimeException ignored) {
        }
    }

/**
 * Transport object for report download responses.
 *
 * @param artifact report artifact metadata
 * @param content HTML payload bytes
 * @param fileName filename suggested for download
 */
    public record DownloadedReport(ArtifactRecord artifact, byte[] content, String fileName) {
        /**
         * Returns report artifact metadata.
         *
         * @return report artifact metadata
         */
        @Override
        public ArtifactRecord artifact() {
            return artifact;
        }
    }
}
