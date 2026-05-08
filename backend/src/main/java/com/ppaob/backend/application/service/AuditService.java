package com.ppaob.backend.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppaob.backend.application.port.out.AuditEventRepositoryPort;
import com.ppaob.backend.application.port.out.UserAccountRepositoryPort;
import com.ppaob.backend.domain.model.AuditEventRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
/**
 * Central service for writing and querying audit events.
 *
 * <p>It resolves a fallback system actor when no actor id is provided, normalizes selected filter
 * fields, constrains pagination values, and serializes event details as JSON for persistence.
 */
public class AuditService {

    private final AuditEventRepositoryPort auditRepository;
    private final UserAccountRepositoryPort userRepository;
    private final ObjectMapper objectMapper;
    private final String systemUserEmail;

/**
 * Creates the audit service.
 *
 * @param auditRepository persistence adapter for audit events
 * @param userRepository repository used to resolve the configured system user
 * @param objectMapper JSON serializer for audit details
 * @param systemUserEmail fallback actor email used when caller omits actor id
 */
    public AuditService(
            AuditEventRepositoryPort auditRepository,
            UserAccountRepositoryPort userRepository,
            ObjectMapper objectMapper,
            @Value("${app.audit.system-user-email:system@ppaob.local}") String systemUserEmail
    ) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.systemUserEmail = systemUserEmail;
    }

/**
 * Appends a new audit event.
 *
 * <p>If {@code actorUserId} is null, the service resolves the configured system user and uses that
 * id as actor. Details are serialized as JSON and replaced with an empty object on serialization
 * errors.
 *
 * <p>Side effects: writes one audit row in persistence.
 *
 * @param action action name (for example LOGIN, REPORT_GENERATE)
 * @param result result label (for example SUCCESS, FAIL)
 * @param actorUserId actor id, or null to use configured system user
 * @param analysisId optional related analysis id
 * @param binaryId optional related binary id
 * @param details optional structured details map
 * @throws java.util.NoSuchElementException when actor is null and configured system user cannot be
 *                                          resolved
 */
    public void log(String action, String result, UUID actorUserId, UUID analysisId, UUID binaryId, Map<String, Object> details) {
        UUID resolvedActor = actorUserId != null
                ? actorUserId
                : userRepository.findByEmail(systemUserEmail).map(user -> user.userId()).orElseThrow();

        auditRepository.append(action, result, resolvedActor, analysisId, binaryId, toJson(details));
    }

/**
 * Returns the most recent audit events.
 *
 * <p>The limit is sanitized to {@code [1, 200]}.
 *
 * @param limit requested maximum row count
 * @return recent audit events ordered by repository policy
 */
    public List<AuditEventRecord> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditRepository.listRecent(safeLimit);
    }

/**
 * Returns audit events matching filter criteria.
 *
 * <p>Normalization rules:
 * - {@code action} and {@code result} are trimmed and uppercased,
 * - blank values are converted to null,
 * - {@code limit} is clamped to {@code [1, 200]},
 * - {@code offset} is clamped to {@code >= 0}.
 *
 * @param filter caller-provided filter criteria
 * @return matching audit events
 */
    public List<AuditEventRecord> listByFilter(AuditEventFilter filter) {
        int safeLimit = Math.max(1, Math.min(filter.limit(), 200));
        int safeOffset = Math.max(0, filter.offset());
        AuditEventFilter sanitized = new AuditEventFilter(
                normalize(filter.action()),
                normalize(filter.result()),
                filter.userId(),
                filter.analysisId(),
                filter.binaryId(),
                filter.from(),
                filter.to(),
                safeLimit,
                safeOffset
        );
        return auditRepository.listByFilter(sanitized);
    }

/**
 * Builds an ordered details map from alternating key/value arguments.
 *
 * <p>Unpaired trailing values are ignored. Keys are stringified through
 * {@link String#valueOf(Object)}.
 *
 * @param kvPairs alternating key/value objects
 * @return ordered map suitable for {@link #log(String, String, UUID, UUID, UUID, Map)}
 */
    public Map<String, Object> details(Object... kvPairs) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            details.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return details;
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
