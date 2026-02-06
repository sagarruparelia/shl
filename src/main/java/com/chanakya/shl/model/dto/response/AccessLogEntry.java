package com.chanakya.shl.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogEntry {

    private String id;
    private String action;
    private String recipient;
    private String ipAddress;
    private String userAgent;
    private boolean success;
    private String failureReason;
    private String createdAt;
}
