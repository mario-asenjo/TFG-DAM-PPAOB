package com.ppaob.backend.adapters.in.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppaob.backend.adapters.in.rest.dto.AnalysisCreateRequest;
import com.ppaob.backend.adapters.in.rest.dto.AnalysisResultResponse;
import com.ppaob.backend.adapters.in.rest.dto.AnalysisResponse;
import com.ppaob.backend.application.service.AnalysisService;
import com.ppaob.backend.domain.model.AnalysisRecord;
import com.ppaob.backend.domain.model.AnalysisResultRecord;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analyses")
/**
 * Handles analysis lifecycle endpoints for requesting and querying analyses.
 */
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a controller with analysis orchestration and JSON decoding support.
     *
     * @param analysisService service that manages analysis requests and retrieval
     * @param objectMapper mapper used to parse persisted result JSON payloads
     */
    public AnalysisController(AnalysisService analysisService, ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Requests a new analysis for an uploaded binary.
     *
     * @param request payload containing binary id and optional analysis profile
     * @param authentication authenticated requester principal
     * @return created analysis metadata with initial status
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @PostMapping
    public AnalysisResponse request(@Valid @RequestBody AnalysisCreateRequest request, Authentication authentication) {
        AuthenticatedUser user = authenticatedUser(authentication);
        boolean requesterIsAdmin = isAdmin(user);
        AnalysisRecord analysis = analysisService.request(
                request.binaryId(),
                request.profile(),
                user.userId(),
                requesterIsAdmin
        );
        return toResponse(analysis);
    }

    /**
     * Retrieves analysis metadata by identifier.
     *
     * @param analysisId analysis identifier
     * @param authentication authenticated requester principal
     * @return analysis metadata for the selected id
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping("/{analysisId}")
    public AnalysisResponse getById(@PathVariable UUID analysisId, Authentication authentication) {
        AuthenticatedUser user = authenticatedUser(authentication);
        boolean requesterIsAdmin = isAdmin(user);
        AnalysisRecord analysis = analysisService.getById(analysisId, user.userId(), requesterIsAdmin);
        return toResponse(analysis);
    }

    /**
     * Retrieves the stored result document associated with an analysis.
     *
     * @param analysisId analysis identifier
     * @param authentication authenticated requester principal
     * @return result payload with schema version and storage timestamp
     * @throws IllegalArgumentException when persisted result JSON cannot be parsed
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping("/{analysisId}/results")
    public AnalysisResultResponse getResultById(@PathVariable UUID analysisId, Authentication authentication) {
        AuthenticatedUser user = authenticatedUser(authentication);
        boolean requesterIsAdmin = isAdmin(user);
        AnalysisResultRecord result = analysisService.getResultByAnalysisId(analysisId, user.userId(), requesterIsAdmin);
        return toResultResponse(result);
    }

    /**
     * Lists analyses visible to the caller, optionally filtered by binary id.
     *
     * @param binaryId optional binary identifier filter
     * @param limit maximum number of results to return
     * @param authentication authenticated requester principal
     * @return analysis metadata collection ordered by service defaults
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping
    public List<AnalysisResponse> list(
            @RequestParam(required = false) UUID binaryId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        AuthenticatedUser user = authenticatedUser(authentication);
        boolean requesterIsAdmin = isAdmin(user);
        return analysisService.list(user.userId(), requesterIsAdmin, binaryId, limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuthenticatedUser authenticatedUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user.roles().contains("ADMIN");
    }

    private AnalysisResponse toResponse(AnalysisRecord analysis) {
        return new AnalysisResponse(
                analysis.analysisId(),
                analysis.binaryId(),
                analysis.binaryOriginalName(),
                analysis.requestedBy(),
                analysis.requestedByEmail(),
                analysis.profile(),
                analysis.status(),
                analysis.createdAt(),
                analysis.startedAt(),
                analysis.finishedAt(),
                analysis.errorSummary()
        );
    }

    private AnalysisResultResponse toResultResponse(AnalysisResultRecord result) {
        String invalidPayloadMessage;

        try {
            return new AnalysisResultResponse(
                    result.analysisId(),
                    result.schemaVersion(),
                    objectMapper.readTree(result.resultsJson()),
                    result.storedAt()
            );
        } catch (JsonProcessingException ex) {
            invalidPayloadMessage = "Invalid analysis result payload for analysisId=" + result.analysisId();
            throw new IllegalArgumentException(invalidPayloadMessage);
        }
    }
}
