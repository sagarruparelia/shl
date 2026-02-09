package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import com.chanakya.shl.exception.InvalidPasscodeException;
import com.chanakya.shl.exception.ShlExpiredException;
import com.chanakya.shl.exception.ShlInactiveException;
import com.chanakya.shl.exception.ShlNotFoundException;
import com.chanakya.shl.model.document.FileDownloadToken;
import com.chanakya.shl.model.document.ShlDocument;
import com.chanakya.shl.model.dto.request.ManifestRequest;
import com.chanakya.shl.model.dto.response.ManifestFileEntry;
import com.chanakya.shl.model.dto.response.ManifestResponse;
import com.chanakya.shl.model.enums.AccessAction;
import com.chanakya.shl.repository.ShlContentRepository;
import com.chanakya.shl.repository.ShlRepository;
import com.chanakya.shl.util.SecureRandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManifestService {

    private final ShlRepository shlRepository;
    private final ShlContentRepository shlContentRepository;
    private final S3StorageService s3StorageService;
    private final ShlPayloadService shlPayloadService;
    private final AccessLogService accessLogService;
    private final AppProperties appProperties;
    private final ReactiveMongoTemplate mongoTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Mono<ManifestResponse> processManifestRequest(String manifestId, ManifestRequest request,
                                                          ServerHttpRequest httpRequest) {
        return shlRepository.findByManifestId(manifestId)
                .switchIfEmpty(Mono.error(new ShlNotFoundException(manifestId)))
                .flatMap(shl -> validateShl(shl, request, httpRequest))
                .flatMap(shl -> buildManifest(shl, request, httpRequest));
    }

    private Mono<ShlDocument> validateShl(ShlDocument shl, ManifestRequest request,
                                           ServerHttpRequest httpRequest) {
        // Deactivated SHLs return "no-longer-valid" status per spec
        if (!shl.isActive()) {
            return accessLogService.logAccess(shl.getId(), AccessAction.MANIFEST_REQUEST,
                            request.getRecipient(), httpRequest, false, "SHL is inactive")
                    .then(Mono.error(new ShlInactiveException(shl.getId())));
        }

        if (shl.getExpiresAt() != null && Instant.now().isAfter(shl.getExpiresAt())) {
            return accessLogService.logAccess(shl.getId(), AccessAction.MANIFEST_REQUEST,
                            request.getRecipient(), httpRequest, false, "SHL is expired")
                    .then(Mono.error(new ShlExpiredException(shl.getId())));
        }

        if (shl.getFlags().contains("P")) {
            return validatePasscode(shl, request, httpRequest);
        }

        return Mono.just(shl);
    }

    private Mono<ShlDocument> validatePasscode(ShlDocument shl, ManifestRequest request,
                                                ServerHttpRequest httpRequest) {
        if (request.getPasscode() == null || request.getPasscode().isBlank()) {
            return accessLogService.logAccess(shl.getId(), AccessAction.PASSCODE_FAILURE,
                            request.getRecipient(), httpRequest, false, "Passcode required but not provided")
                    .then(Mono.error(new InvalidPasscodeException(
                            shl.getPasscodeFailuresRemaining() != null ? shl.getPasscodeFailuresRemaining() : 0)));
        }

        // Atomic decrement of passcodeFailuresRemaining
        Query query = Query.query(Criteria.where("id").is(shl.getId())
                .and("passcodeFailuresRemaining").gt(0));
        Update update = new Update().inc("passcodeFailuresRemaining", -1);

        return mongoTemplate.findAndModify(query, update,
                        FindAndModifyOptions.options().returnNew(true), ShlDocument.class)
                .switchIfEmpty(Mono.defer(() -> {
                    // No document found means either doesn't exist or attempts exhausted
                    shl.setActive(false);
                    return shlRepository.save(shl)
                            .then(accessLogService.logAccess(shl.getId(), AccessAction.PASSCODE_FAILURE,
                                    request.getRecipient(), httpRequest, false, "Passcode attempts exhausted"))
                            .then(Mono.error(new InvalidPasscodeException(0)));
                }))
                .flatMap(decrementedShl -> {
                    if (passwordEncoder.matches(request.getPasscode(), decrementedShl.getPasscodeHash())) {
                        // Passcode correct — restore the decremented attempt
                        Query restoreQuery = Query.query(Criteria.where("id").is(shl.getId()));
                        Update restoreUpdate = new Update().inc("passcodeFailuresRemaining", 1);
                        return mongoTemplate.findAndModify(restoreQuery, restoreUpdate, ShlDocument.class)
                                .thenReturn(shl);
                    } else {
                        int remaining = decrementedShl.getPasscodeFailuresRemaining() != null
                                ? decrementedShl.getPasscodeFailuresRemaining() : 0;
                        return accessLogService.logAccess(shl.getId(), AccessAction.PASSCODE_FAILURE,
                                        request.getRecipient(), httpRequest, false, "Invalid passcode")
                                .then(Mono.error(new InvalidPasscodeException(remaining)));
                    }
                });
    }

    private Mono<ManifestResponse> buildManifest(ShlDocument shl, ManifestRequest request,
                                                   ServerHttpRequest httpRequest) {
        return shlContentRepository.findByShlId(shl.getId())
                .flatMap(content -> {
                    Integer maxEmbedded = request.getEmbeddedLengthMax();

                    // Try to serve as embedded if the file is small enough
                    if (maxEmbedded != null && maxEmbedded > 0) {
                        return s3StorageService.downloadPayload(content.getS3Key())
                                .flatMap(jweString -> {
                                    String lastUpdated = content.getCreatedAt() != null ? content.getCreatedAt().toString() : null;
                                    if (jweString.length() <= maxEmbedded) {
                                        return Mono.just(ManifestFileEntry.builder()
                                                .contentType(content.getContentType())
                                                .embedded(jweString)
                                                .lastUpdated(lastUpdated)
                                                .build());
                                    } else {
                                        return createFileToken(shl, content.getId())
                                                .map(token -> ManifestFileEntry.builder()
                                                        .contentType(content.getContentType())
                                                        .location(shlPayloadService.buildFileDownloadUrl(token.getId()))
                                                        .lastUpdated(lastUpdated)
                                                        .build());
                                    }
                                });
                    }

                    // Default: create download token
                    String lastUpdated = content.getCreatedAt() != null ? content.getCreatedAt().toString() : null;
                    return createFileToken(shl, content.getId())
                            .map(token -> ManifestFileEntry.builder()
                                    .contentType(content.getContentType())
                                    .location(shlPayloadService.buildFileDownloadUrl(token.getId()))
                                    .lastUpdated(lastUpdated)
                                    .build());
                })
                .collectList()
                .flatMap(files -> {
                    // If single-use, deactivate after manifest fetch
                    Mono<Void> deactivateMono = Mono.empty();
                    if (shl.isSingleUse()) {
                        shl.setActive(false);
                        deactivateMono = shlRepository.save(shl).then();
                    }

                    String status = shl.getFlags().contains("L") ? "can-change" : "finalized";

                    return deactivateMono
                            .then(accessLogService.logAccess(shl.getId(), AccessAction.MANIFEST_REQUEST,
                                    request.getRecipient(), httpRequest, true, null))
                            .thenReturn(ManifestResponse.builder().status(status).files(files).build());
                });
    }

    private Mono<FileDownloadToken> createFileToken(ShlDocument shl, String contentId) {
        FileDownloadToken token = FileDownloadToken.builder()
                .id(SecureRandomUtil.generateBase64UrlRandom(32))
                .contentId(contentId)
                .shlId(shl.getId())
                .expiresAt(Instant.now().plus(appProperties.getFileTokenTtlMinutes(), ChronoUnit.MINUTES))
                .consumed(false)
                .build();
        return mongoTemplate.save(token);
    }

    public Mono<String> downloadFile(String tokenId, ServerHttpRequest httpRequest) {
        // Atomically find and mark token as consumed
        Query tokenQuery = Query.query(Criteria.where("id").is(tokenId)
                .and("consumed").is(false));
        Update tokenUpdate = new Update().set("consumed", true);

        return mongoTemplate.findAndModify(tokenQuery, tokenUpdate, FileDownloadToken.class)
                .switchIfEmpty(Mono.error(new ShlNotFoundException("Token not found or already consumed: " + tokenId)))
                .flatMap(token -> {
                    if (token.getExpiresAt() != null && Instant.now().isAfter(token.getExpiresAt())) {
                        return Mono.error(new ShlExpiredException("Download token expired"));
                    }

                    return shlContentRepository.findById(token.getContentId())
                            .flatMap(content ->
                                    accessLogService.logAccess(content.getShlId(), AccessAction.FILE_DOWNLOAD,
                                                    null, httpRequest, true, null)
                                            .then(s3StorageService.downloadPayload(content.getS3Key()))
                            );
                });
    }

    /**
     * Spec-compliant U-flag direct access: returns raw JWE string.
     * Per SHL spec, GET to the manifest URL with ?recipient= returns
     * the encrypted file directly with Content-Type: application/jose.
     */
    public Mono<String> processDirectAccessRawJwe(String manifestId, String recipient,
                                                   ServerHttpRequest httpRequest) {
        return shlRepository.findByManifestId(manifestId)
                .switchIfEmpty(Mono.error(new ShlNotFoundException(manifestId)))
                .flatMap(shl -> {
                    // For U-flag direct access, inactive SHLs return 404 (no manifest wrapper for "no-longer-valid")
                    if (!shl.isActive()) {
                        return Mono.error(new ShlNotFoundException(shl.getId()));
                    }
                    if (shl.getExpiresAt() != null && Instant.now().isAfter(shl.getExpiresAt())) {
                        return Mono.error(new ShlExpiredException(shl.getId()));
                    }
                    if (!shl.getFlags().contains("U")) {
                        return Mono.error(new IllegalArgumentException("Direct access requires U flag"));
                    }

                    return shlContentRepository.findByShlId(shl.getId())
                            .next()
                            .flatMap(content -> {
                                Mono<Void> deactivateMono = Mono.empty();
                                if (shl.isSingleUse()) {
                                    shl.setActive(false);
                                    deactivateMono = shlRepository.save(shl).then();
                                }

                                return deactivateMono
                                        .then(accessLogService.logAccess(shl.getId(), AccessAction.DIRECT_ACCESS,
                                                recipient, httpRequest, true, null))
                                        .then(s3StorageService.downloadPayload(content.getS3Key()));
                            });
                });
    }

}
