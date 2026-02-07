package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import com.chanakya.shl.exception.ShlNotFoundException;
import com.chanakya.shl.model.document.ShlContentDocument;
import com.chanakya.shl.model.document.ShlDocument;
import com.chanakya.shl.model.dto.request.CreateShlRequest;
import com.chanakya.shl.model.dto.response.CreateShlResponse;
import com.chanakya.shl.model.dto.response.ShlDetailResponse;
import com.chanakya.shl.model.dto.response.ShlSummaryResponse;
import com.chanakya.shl.model.enums.ShlFlag;
import com.chanakya.shl.repository.ShlContentRepository;
import com.chanakya.shl.repository.ShlRepository;
import com.chanakya.shl.util.SecureRandomUtil;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShlService {

    private final ShlRepository shlRepository;
    private final ShlContentRepository shlContentRepository;
    private final EncryptionService encryptionService;
    private final S3StorageService s3StorageService;
    private final ShlPayloadService shlPayloadService;
    private final QrCodeService qrCodeService;
    private final AccessLogService accessLogService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Mono<CreateShlResponse> createFromJson(CreateShlRequest request) {
        return Mono.defer(() -> {
            validateFlags(request);

            String encryptionKey = SecureRandomUtil.generateBase64UrlRandom(32);
            String manifestId = SecureRandomUtil.generateBase64UrlRandom(32);
            String flags = ShlFlag.toFlagString(request.isLongTerm(),
                    request.getPasscode() != null, request.isSingleUse());

            ShlDocument shl = ShlDocument.builder()
                    .manifestId(manifestId)
                    .encryptionKey(encryptionKey)
                    .label(request.getLabel())
                    .flags(flags)
                    .passcodeHash(request.getPasscode() != null ?
                            passwordEncoder.encode(request.getPasscode()) : null)
                    .passcodeFailuresRemaining(request.getPasscode() != null ?
                            appProperties.getDefaultPasscodeAttempts() : null)
                    .expiresAt(request.getExpirationInSeconds() != null ?
                            Instant.now().plusSeconds(request.getExpirationInSeconds()) : null)
                    .active(true)
                    .singleUse(request.isSingleUse())
                    .build();

            return shlRepository.save(shl)
                    .flatMap(savedShl -> {
                        try {
                            String contentJson = objectMapper.writeValueAsString(request.getContent());
                            return encryptAndStore(savedShl, contentJson, "application/fhir+json", null);
                        } catch (Exception e) {
                            return Mono.error(new RuntimeException("Failed to serialize content", e));
                        }
                    })
                    .flatMap(this::toCreateResponse);
        });
    }

    public Mono<CreateShlResponse> createFromFile(byte[] fileBytes, String contentType,
                                                    String originalFileName, String label,
                                                    String passcode, Long expirationInSeconds,
                                                    boolean singleUse, boolean longTerm) {
        return Mono.defer(() -> {
            if (singleUse && longTerm) {
                return Mono.error(new IllegalArgumentException("Cannot combine single-use (U) with long-term (L) flag"));
            }
            if (singleUse && passcode != null) {
                return Mono.error(new IllegalArgumentException("Cannot combine single-use (U) with passcode (P) flag"));
            }

            String encryptionKey = SecureRandomUtil.generateBase64UrlRandom(32);
            String manifestId = SecureRandomUtil.generateBase64UrlRandom(32);
            String flags = ShlFlag.toFlagString(longTerm, passcode != null, singleUse);

            ShlDocument shl = ShlDocument.builder()
                    .manifestId(manifestId)
                    .encryptionKey(encryptionKey)
                    .label(label)
                    .flags(flags)
                    .passcodeHash(passcode != null ? passwordEncoder.encode(passcode) : null)
                    .passcodeFailuresRemaining(passcode != null ?
                            appProperties.getDefaultPasscodeAttempts() : null)
                    .expiresAt(expirationInSeconds != null ?
                            Instant.now().plusSeconds(expirationInSeconds) : null)
                    .active(true)
                    .singleUse(singleUse)
                    .build();

            return shlRepository.save(shl)
                    .flatMap(savedShl -> {
                        String fileContent = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
                        return encryptAndStore(savedShl, fileContent, contentType, originalFileName);
                    })
                    .flatMap(this::toCreateResponse);
        });
    }

    private Mono<ShlDocument> encryptAndStore(ShlDocument shl, String content,
                                               String contentType, String originalFileName) {
        return encryptionService.encrypt(content, shl.getEncryptionKey(), contentType)
                .flatMap(jweString -> {
                    ShlContentDocument contentDoc = ShlContentDocument.builder()
                            .shlId(shl.getId())
                            .contentType(contentType)
                            .originalFileName(originalFileName)
                            .contentLength(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                            .build();

                    return shlContentRepository.save(contentDoc)
                            .flatMap(savedContent -> {
                                String s3Key = s3StorageService.buildPayloadKey(shl.getId(), savedContent.getId());
                                savedContent.setS3Key(s3Key);
                                return shlContentRepository.save(savedContent)
                                        .then(s3StorageService.uploadPayload(s3Key, jweString));
                            });
                })
                .thenReturn(shl);
    }

    public Flux<ShlSummaryResponse> listShls(Boolean active, Pageable pageable) {
        Flux<ShlDocument> shls;
        if (active != null) {
            shls = shlRepository.findByActive(active, pageable);
        } else {
            shls = shlRepository.findAllBy(pageable);
        }
        return shls.flatMap(this::toSummaryResponse);
    }

    public Mono<Long> countShls(Boolean active) {
        if (active != null) {
            return shlRepository.countByActive(active);
        }
        return shlRepository.count();
    }

    public Mono<ShlDetailResponse> getShlDetail(String id) {
        return shlRepository.findById(id)
                .switchIfEmpty(Mono.error(new ShlNotFoundException(id)))
                .flatMap(shl -> {
                    String shlinkUrl = shlPayloadService.buildShlinkUrl(shl);

                    Mono<java.util.List<ShlDetailResponse.ContentSummary>> contentsMono =
                            shlContentRepository.findByShlId(id)
                                    .map(c -> ShlDetailResponse.ContentSummary.builder()
                                            .id(c.getId())
                                            .contentType(c.getContentType())
                                            .originalFileName(c.getOriginalFileName())
                                            .contentLength(c.getContentLength())
                                            .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                                            .build())
                                    .collectList();

                    Mono<Long> accessCountMono = accessLogService.getAccessCount(id);

                    Mono<String> qrCodeMono = qrCodeService.generateBase64DataUri(
                            shlinkUrl, appProperties.getQrCodeDefaultSize());

                    return Mono.zip(contentsMono, accessCountMono, qrCodeMono)
                            .map(tuple -> ShlDetailResponse.builder()
                                    .id(shl.getId())
                                    .label(shl.getLabel())
                                    .flags(shl.getFlags())
                                    .active(shl.isActive())
                                    .singleUse(shl.isSingleUse())
                                    .expiresAt(shl.getExpiresAt() != null ? shl.getExpiresAt().toString() : null)
                                    .createdAt(shl.getCreatedAt() != null ? shl.getCreatedAt().toString() : null)
                                    .updatedAt(shl.getUpdatedAt() != null ? shl.getUpdatedAt().toString() : null)
                                    .shlinkUrl(shlinkUrl)
                                    .qrCode(tuple.getT3())
                                    .contents(tuple.getT1())
                                    .totalAccesses(tuple.getT2())
                                    .build());
                });
    }

    public Mono<Void> deactivate(String id) {
        return shlRepository.findById(id)
                .switchIfEmpty(Mono.error(new ShlNotFoundException(id)))
                .flatMap(shl -> {
                    shl.setActive(false);
                    return shlRepository.save(shl);
                })
                .then();
    }

    private void validateFlags(CreateShlRequest request) {
        if (request.isSingleUse() && request.isLongTerm()) {
            throw new IllegalArgumentException("Cannot combine single-use (U) with long-term (L) flag");
        }
        if (request.isSingleUse() && request.getPasscode() != null) {
            throw new IllegalArgumentException("Cannot combine single-use (U) with passcode (P) flag");
        }
    }

    private Mono<CreateShlResponse> toCreateResponse(ShlDocument shl) {
        String shlinkUrl = shlPayloadService.buildShlinkUrl(shl);
        return qrCodeService.generateBase64DataUri(shlinkUrl, appProperties.getQrCodeDefaultSize())
                .map(qrCode -> CreateShlResponse.builder()
                        .id(shl.getId())
                        .shlinkUrl(shlinkUrl)
                        .qrCode(qrCode)
                        .managementUrl(shlPayloadService.buildManagementUrl(shl.getId()))
                        .label(shl.getLabel())
                        .flags(shl.getFlags())
                        .expiresAt(shl.getExpiresAt() != null ? shl.getExpiresAt().toString() : null)
                        .singleUse(shl.isSingleUse())
                        .build());
    }

    private Mono<ShlSummaryResponse> toSummaryResponse(ShlDocument shl) {
        return Mono.zip(
                shlContentRepository.countByShlId(shl.getId()),
                accessLogService.getAccessCount(shl.getId())
        ).map(tuple -> ShlSummaryResponse.builder()
                .id(shl.getId())
                .label(shl.getLabel())
                .flags(shl.getFlags())
                .active(shl.isActive())
                .singleUse(shl.isSingleUse())
                .expiresAt(shl.getExpiresAt() != null ? shl.getExpiresAt().toString() : null)
                .createdAt(shl.getCreatedAt() != null ? shl.getCreatedAt().toString() : null)
                .contentCount(tuple.getT1().intValue())
                .accessCount(tuple.getT2())
                .build());
    }
}
