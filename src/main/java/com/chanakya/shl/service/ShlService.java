package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import com.chanakya.shl.exception.ShlNotFoundException;
import com.chanakya.shl.model.document.ShlContentDocument;
import com.chanakya.shl.model.document.ShlDocument;
import com.chanakya.shl.model.dto.request.CreateShlRequest;
import com.chanakya.shl.model.dto.response.CreateShlResponse;
import com.chanakya.shl.model.dto.response.ShlDetailResponse;
import com.chanakya.shl.model.dto.response.ShlSummaryResponse;
import com.chanakya.shl.model.enums.FhirCategory;
import com.chanakya.shl.model.enums.ShlFlag;
import com.chanakya.shl.repository.ShlContentRepository;
import com.chanakya.shl.repository.ShlRepository;
import com.chanakya.shl.util.FhirDocumentReferenceUtil;
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
import java.util.List;

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
    private final HealthLakeService healthLakeService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Mono<CreateShlResponse> createFromJson(CreateShlRequest request) {
        return Mono.defer(() -> {
            validateFlags(request);
            validateDataSource(request);

            String encryptionKey = SecureRandomUtil.generateBase64UrlRandom(32);
            String manifestId = SecureRandomUtil.generateBase64UrlRandom(32);
            String flags = ShlFlag.toFlagString(request.isLongTerm(),
                    request.getPasscode() != null, request.isDirectAccess());

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
                    .flatMap(savedShl -> storeAllContent(savedShl, request))
                    .flatMap(this::toCreateResponse);
        });
    }

    private Mono<ShlDocument> storeAllContent(ShlDocument shl, CreateShlRequest request) {
        Mono<ShlDocument> healthLakeMono = Mono.just(shl);

        List<FhirCategory> categories = request.getCategories();
        if (categories != null && !categories.isEmpty()) {
            healthLakeMono = healthLakeService.fetchBundles(request.getPatientId(), categories)
                    .concatMap(bundleJson -> encryptAndStore(shl, bundleJson,
                            "application/fhir+json;fhirVersion=4.0.1", null, null, 0))
                    .then(Mono.just(shl));
        }

        return healthLakeMono.flatMap(s -> {
            if (request.getContent() != null) {
                try {
                    String contentJson = objectMapper.writeValueAsString(request.getContent());
                    return encryptAndStore(s, contentJson, "application/fhir+json;fhirVersion=4.0.1", null, null, 0);
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("Failed to serialize content", e));
                }
            }
            return Mono.just(s);
        });
    }

    public Mono<CreateShlResponse> createFromFile(byte[] fileBytes, String contentType,
                                                    String originalFileName, String label,
                                                    String passcode, Long expirationInSeconds,
                                                    boolean singleUse, boolean directAccess,
                                                    boolean longTerm,
                                                    String patientId, List<FhirCategory> categories) {
        return Mono.defer(() -> {
            if (directAccess && longTerm) {
                return Mono.error(new IllegalArgumentException("Cannot combine direct access (U) with long-term (L) flag"));
            }
            if (directAccess && passcode != null) {
                return Mono.error(new IllegalArgumentException("Cannot combine direct access (U) with passcode (P) flag"));
            }

            String encryptionKey = SecureRandomUtil.generateBase64UrlRandom(32);
            String manifestId = SecureRandomUtil.generateBase64UrlRandom(32);
            String flags = ShlFlag.toFlagString(longTerm, passcode != null, directAccess);

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
                        // Store HealthLake bundles if categories provided
                        Mono<ShlDocument> healthLakeMono = Mono.just(savedShl);
                        if (categories != null && !categories.isEmpty() && patientId != null) {
                            healthLakeMono = healthLakeService.fetchBundles(patientId, categories)
                                    .concatMap(bundleJson -> encryptAndStore(savedShl, bundleJson,
                                            "application/fhir+json;fhirVersion=4.0.1", null, null, 0))
                                    .then(Mono.just(savedShl));
                        }
                        // Then store the uploaded file
                        return healthLakeMono.flatMap(s -> {
                            String manifestContentType;
                            String fileContent;
                            String origType = null;
                            int origLength = 0;

                            if (FhirDocumentReferenceUtil.isShlCompliantContentType(contentType)) {
                                fileContent = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
                                manifestContentType = contentType;
                            } else {
                                fileContent = FhirDocumentReferenceUtil.wrapInDocumentReference(
                                        fileBytes, contentType, originalFileName);
                                manifestContentType = "application/fhir+json;fhirVersion=4.0.1";
                                origType = contentType;
                                origLength = fileBytes.length;
                            }
                            return encryptAndStore(s, fileContent, manifestContentType,
                                    originalFileName, origType, origLength);
                        });
                    })
                    .flatMap(this::toCreateResponse);
        });
    }

    private Mono<ShlDocument> encryptAndStore(ShlDocument shl, String content,
                                               String contentType, String originalFileName,
                                               String originalContentType, int originalContentLength) {
        return encryptionService.encrypt(content, shl.getEncryptionKey(), contentType)
                .flatMap(jweString -> {
                    ShlContentDocument contentDoc = ShlContentDocument.builder()
                            .shlId(shl.getId())
                            .contentType(contentType)
                            .originalFileName(originalFileName)
                            .originalContentType(originalContentType)
                            .contentLength(originalContentLength > 0 ? originalContentLength :
                                    content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
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
                                            .contentType(c.getOriginalContentType() != null ?
                                                    c.getOriginalContentType() : c.getContentType())
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
        if (request.isDirectAccess() && request.isLongTerm()) {
            throw new IllegalArgumentException("Cannot combine direct access (U) with long-term (L) flag");
        }
        if (request.isDirectAccess() && request.getPasscode() != null) {
            throw new IllegalArgumentException("Cannot combine direct access (U) with passcode (P) flag");
        }
    }

    private void validateDataSource(CreateShlRequest request) {
        boolean hasCategories = request.getCategories() != null && !request.getCategories().isEmpty();
        boolean hasContent = request.getContent() != null;

        if (!hasCategories && !hasContent) {
            throw new IllegalArgumentException("At least one data source is required: provide 'content' or 'categories' with 'patientId'");
        }
        if (hasCategories && (request.getPatientId() == null || request.getPatientId().isBlank())) {
            throw new IllegalArgumentException("'patientId' is required when 'categories' are specified");
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
