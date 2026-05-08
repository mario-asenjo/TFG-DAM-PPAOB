package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.BinaryListItemResponse;
import com.ppaob.backend.adapters.in.rest.dto.BinaryUploadResponse;
import com.ppaob.backend.application.service.AuditService;
import com.ppaob.backend.application.service.BinaryService;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/binaries")
/**
 * Handles binary upload and listing endpoints for authenticated users.
 */
public class BinaryController {

    private final BinaryService binaryService;
    private final AuditService auditService;
    private final String bucketName;

    /**
     * Creates a controller with binary storage and audit dependencies.
     *
     * @param binaryService service that validates and stores uploaded binaries
     * @param auditService service used to record best-effort audit events
     * @param bucketName object storage bucket where binaries are managed
     */
    public BinaryController(BinaryService binaryService, AuditService auditService, @Value("${storage.s3.bucket}") String bucketName) {
        this.binaryService = binaryService;
        this.auditService = auditService;
        this.bucketName = bucketName;
    }

    /**
     * Uploads a binary file and records an audit event on success.
     *
     * @param file multipart file content to ingest
     * @param authentication authenticated uploader principal
     * @return upload metadata including deduplication and object restoration flags
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     * @implNote Audit logging is best-effort and does not fail the request if logging fails.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BinaryUploadResponse upload(@RequestPart("file") MultipartFile file, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        var result = binaryService.uploadBinary(file, user.userId(), bucketName);
        var binary = result.binary();
        safeAudit(
                "BINARY_UPLOAD",
                "SUCCESS",
                user.userId(),
                null,
                binary.binaryId(),
                auditService.details(
                        "binaryId", binary.binaryId(),
                        "sha256", binary.sha256(),
                        "deduplicated", result.deduplicated(),
                        "restoredObject", result.restoredObject(),
                        "sizeBytes", binary.sizeBytes()
                )
        );

        return new BinaryUploadResponse(
                binary.binaryId(),
                binary.originalName(),
                binary.sha256(),
                binary.format(),
                binary.sizeBytes(),
                binary.uploadedAt(),
                result.deduplicated(),
                result.restoredObject()
        );
    }

    /**
     * Lists binaries uploaded by the authenticated user.
     *
     * @param authentication authenticated requester principal
     * @return binary metadata list scoped to the uploader
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     * @implNote Audit logging is best-effort and does not fail the request if logging fails.
     */
    @GetMapping
    public List<BinaryListItemResponse> list(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        List<BinaryListItemResponse> response = binaryService.listByUploader(user.userId()).stream()
                .map(binary -> new BinaryListItemResponse(
                        binary.binaryId(),
                        binary.originalName(),
                        binary.sha256(),
                        binary.format(),
                        binary.sizeBytes(),
                        binary.uploadedAt(),
                        binary.objectAvailable()
                ))
                .toList();
        safeAudit("BINARY_LIST", "SUCCESS", user.userId(), null, null,
                auditService.details("count", response.size()));
        return response;
    }

    private void safeAudit(String action, String result, java.util.UUID userId, java.util.UUID analysisId, java.util.UUID binaryId, java.util.Map<String, Object> details) {
        try {
            auditService.log(action, result, userId, analysisId, binaryId, details);
        } catch (RuntimeException ignored) {
        }
    }
}
