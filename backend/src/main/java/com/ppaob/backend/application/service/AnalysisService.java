package com.ppaob.backend.application.service;

import com.ppaob.backend.application.port.out.AnalysisRepositoryPort;
import com.ppaob.backend.domain.model.AnalysisRecord;
import com.ppaob.backend.domain.model.AnalysisResultRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
/**
 * Coordinates analysis requests and read access to analysis data.
 *
 * <p>This service validates analysis profile selection, verifies binary ownership/visibility,
 * ensures the referenced object exists in storage, and delegates persistence operations to
 * {@link AnalysisRepositoryPort}. Successful analysis requests are audited on a best-effort basis.
 */
public class AnalysisService {

    public static final String PROFILE_STATIC_BASELINE = "STATIC_BASELINE";
    public static final String PROFILE_DYNAMIC_BASELINE = "DYNAMIC_BASELINE";
    private static final Set<String> SUPPORTED_PROFILES = Set.copyOf(new LinkedHashSet<>(List.of(
            PROFILE_STATIC_BASELINE,
            PROFILE_DYNAMIC_BASELINE
    )));

    private final AnalysisRepositoryPort analyses;
    private final BinaryService binaries;
    private final AuditService auditService;

/**
 * Creates the analysis service.
 *
 * @param analyses analysis repository for create/read operations
 * @param binaries binary service used for authorization and storage-availability checks
 * @param auditService audit logger used for best-effort request tracing
 */
    public AnalysisService(AnalysisRepositoryPort analyses, BinaryService binaries, AuditService auditService) {
        this.analyses = analyses;
        this.binaries = binaries;
        this.auditService = auditService;
    }

    @Transactional
/**
 * Creates a new analysis request for a binary visible to the requester.
 *
 * <p>Business rules:
 * - blank/null profiles default to {@code STATIC_BASELINE},
 * - only supported profiles are accepted,
 * - request is allowed only if the binary is visible to the requester (or requester is admin),
 * - request is rejected when the binary object is missing in storage.
 *
 * <p>Side effects: creates a new analysis record and emits a best-effort
 * {@code ANALYSIS_REQUEST/SUCCESS} audit event.
 *
 * @param binaryId binary to analyze
 * @param profile requested analysis profile
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return created analysis record
 * @throws IllegalArgumentException when profile is unsupported, binary is inaccessible,
 *                                  binary object is unavailable, or repository creation fails
 */
    public AnalysisRecord request(UUID binaryId, String profile, UUID requesterId, boolean requesterIsAdmin) {
        String normalizedProfile = normalizeProfile(profile);

        var binary = binaries.requireBinaryForRequester(binaryId, requesterId, requesterIsAdmin);
        if (!binaries.isBinaryObjectAvailable(binary)) {
            throw new IllegalArgumentException("Binary object is not available in storage. Re-upload the binary to enable analysis.");
        }

        AnalysisRecord analysis = analyses.create(binaryId, requesterId, normalizedProfile)
                .orElseThrow(() -> new IllegalArgumentException("Binary not found for current user"));

        safeAudit(
                "ANALYSIS_REQUEST",
                "SUCCESS",
                requesterId,
                analysis.analysisId(),
                analysis.binaryId(),
                auditService.details(
                        "requestedProfile", normalizedProfile,
                        "profile", analysis.profile(),
                        "status", analysis.status()
                )
        );

        return analysis;
    }

/**
 * Returns an analysis record if visible to the requester.
 *
 * @param analysisId analysis identifier
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return analysis record
 * @throws IllegalArgumentException when analysis is not found or not visible
 */
    public AnalysisRecord getById(UUID analysisId, UUID requesterId, boolean requesterIsAdmin) {
        return analyses.findById(analysisId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found"));
    }

/**
 * Returns the stored analysis result for an analysis visible to the requester.
 *
 * @param analysisId analysis identifier
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @return persisted analysis result record
 * @throws IllegalArgumentException when no result is found or requester has no visibility
 */
    public AnalysisResultRecord getResultByAnalysisId(UUID analysisId, UUID requesterId, boolean requesterIsAdmin) {
        return analyses.findResultByAnalysisId(analysisId, requesterId, requesterIsAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Analysis result not found"));
    }

/**
 * Lists analyses visible to the requester with optional binary filtering.
 *
 * <p>The limit is sanitized to the inclusive range {@code [1, 200]}.
 *
 * @param requesterId authenticated requester user id
 * @param requesterIsAdmin whether requester has admin visibility
 * @param binaryId optional binary filter; {@code null} means no filter
 * @param limit requested page size
 * @return list of analysis records matching requester visibility and filters
 */
    public List<AnalysisRecord> list(UUID requesterId, boolean requesterIsAdmin, UUID binaryId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return analyses.list(requesterId, requesterIsAdmin, binaryId, safeLimit);
    }

    private String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return PROFILE_STATIC_BASELINE;
        }

        String normalized = profile.trim().toUpperCase();
        if (!SUPPORTED_PROFILES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported analysis profile: " + profile);
        }

        return normalized;
    }

    private void safeAudit(String action, String result, UUID userId, UUID analysisId, UUID binaryId, java.util.Map<String, Object> details) {
        try {
            auditService.log(action, result, userId, analysisId, binaryId, details);
        } catch (RuntimeException ignored) {
        }
    }
}
