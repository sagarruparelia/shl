package com.chanakya.shl.model.enums;

import lombok.Getter;

@Getter
public enum FhirCategory {

    IMMUNIZATIONS("Immunization", "Immunizations"),
    CONDITIONS("Condition", "Conditions"),
    MEDICATIONS("MedicationRequest", "Medications"),
    ALLERGIES("AllergyIntolerance", "Allergies"),
    LAB_RESULTS("Observation", "Lab Results"),
    PROCEDURES("Procedure", "Procedures");

    private final String resourceType;
    private final String displayName;

    FhirCategory(String resourceType, String displayName) {
        this.resourceType = resourceType;
        this.displayName = displayName;
    }
}
