package com.chanakya.shl.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShlResponse {

    private String id;
    private String shlinkUrl;
    private String qrCode;
    private String managementUrl;
    private String label;
    private String flags;
    private String expiresAt;
    private boolean singleUse;
}
