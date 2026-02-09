package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import com.chanakya.shl.model.ShlPayload;
import com.chanakya.shl.model.document.ShlDocument;
import com.chanakya.shl.util.Base64UrlUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShlPayloadService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private static final int MAX_MANIFEST_URL_LENGTH = 128;

    public String buildShlinkUrl(ShlDocument shl) {
        String manifestUrl = appProperties.getBaseUrl() + "/api/shl/manifest/" + shl.getManifestId();

        if (manifestUrl.length() > MAX_MANIFEST_URL_LENGTH) {
            log.warn("Manifest URL exceeds spec limit of {} chars (actual: {}): {}",
                    MAX_MANIFEST_URL_LENGTH, manifestUrl.length(), manifestUrl);
        }

        ShlPayload payload = ShlPayload.builder()
                .url(manifestUrl)
                .key(shl.getEncryptionKey())
                .exp(shl.getExpiresAt() != null ? shl.getExpiresAt().getEpochSecond() : null)
                .flag(shl.getFlags().isEmpty() ? null : shl.getFlags())
                .label(shl.getLabel())
                .build();

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String encodedPayload = Base64UrlUtil.encode(payloadJson);
            return appProperties.getBaseUrl() + appProperties.getViewerPath() + "#shlink:/" + encodedPayload;
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize SHL payload", e);
        }
    }

    public String buildManagementUrl(String shlId) {
        return appProperties.getBaseUrl() + "/api/shl/" + shlId;
    }

    public String buildFileDownloadUrl(String tokenId) {
        return appProperties.getBaseUrl() + "/api/shl/file/" + tokenId;
    }
}
