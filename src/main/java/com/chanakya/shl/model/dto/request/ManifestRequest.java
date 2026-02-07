package com.chanakya.shl.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManifestRequest {

    @NotBlank(message = "recipient is required")
    private String recipient;

    private String passcode;

    private Integer embeddedLengthMax;
}
