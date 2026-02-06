package com.chanakya.shl.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShlDetailResponse {

    private String id;
    private String label;
    private String flags;
    private boolean active;
    private boolean singleUse;
    private String expiresAt;
    private String createdAt;
    private String updatedAt;
    private String shlinkUrl;
    private String qrCodeUrl;
    private List<ContentSummary> contents;
    private long totalAccesses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentSummary {
        private String id;
        private String contentType;
        private String originalFileName;
        private long contentLength;
        private String createdAt;
    }
}
