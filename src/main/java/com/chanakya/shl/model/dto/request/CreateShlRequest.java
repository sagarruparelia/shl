package com.chanakya.shl.model.dto.request;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShlRequest {

    private JsonNode content;

    @Size(max = 80, message = "Label must not exceed 80 characters")
    private String label;

    private String passcode;

    private Long expirationInSeconds;

    private boolean singleUse;

    private boolean longTerm;
}
