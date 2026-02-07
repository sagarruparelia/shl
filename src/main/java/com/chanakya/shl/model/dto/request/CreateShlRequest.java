package com.chanakya.shl.model.dto.request;

import com.chanakya.shl.model.enums.FhirCategory;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShlRequest {

    private JsonNode content;

    private String patientId;

    private List<FhirCategory> categories;

    @Size(max = 80, message = "Label must not exceed 80 characters")
    private String label;

    private String passcode;

    private Long expirationInSeconds;

    @Builder.Default
    private boolean singleUse = false;

    @Builder.Default
    private boolean longTerm = false;
}
