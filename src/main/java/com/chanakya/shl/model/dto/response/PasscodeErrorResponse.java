package com.chanakya.shl.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasscodeErrorResponse {

    private String error;
    private int remainingAttempts;
}
