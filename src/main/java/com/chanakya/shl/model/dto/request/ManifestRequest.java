package com.chanakya.shl.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManifestRequest {

    private String recipient;

    private String passcode;

    private Integer embeddedLengthMax;
}
