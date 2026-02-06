package com.chanakya.shl.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShlSummaryResponse {

    private String id;
    private String label;
    private String flags;
    private boolean active;
    private boolean singleUse;
    private String expiresAt;
    private String createdAt;
    private int contentCount;
    private long accessCount;
}
