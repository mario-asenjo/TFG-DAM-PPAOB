package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.AuditEventResponse;
import com.ppaob.backend.application.service.AuditEventFilter;
import com.ppaob.backend.application.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
/**
 * Provides read-only access to audit events for administrative inspection.
 */
public class AuditController {

    private final AuditService auditService;

    /**
     * Creates a controller using the audit service.
     *
     * @param auditService service that applies filters and fetches audit events
     */
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Lists audit events matching optional filters.
     *
     * @param action optional action filter
     * @param result optional result filter
     * @param userId optional actor identifier filter
     * @param analysisId optional analysis identifier filter
     * @param binaryId optional binary identifier filter
     * @param from optional inclusive lower timestamp bound
     * @param to optional inclusive upper timestamp bound
     * @param limit maximum number of events to return
     * @param offset result offset for pagination
     * @return audit events projected for API responses
     */
    @GetMapping("/events")
    public List<AuditEventResponse> listEvents(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID analysisId,
            @RequestParam(required = false) UUID binaryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        AuditEventFilter filter = new AuditEventFilter(
                action,
                result,
                userId,
                analysisId,
                binaryId,
                from,
                to,
                limit,
                offset
        );
        return auditService.listByFilter(filter).stream()
                .map(event -> new AuditEventResponse(
                        event.eventId(),
                        event.ts(),
                        event.action(),
                        event.result(),
                        event.userId(),
                        event.userEmail(),
                        event.analysisId(),
                        event.binaryId(),
                        event.binaryOriginalName(),
                        event.detailsJson()
                ))
                .toList();
    }
}
