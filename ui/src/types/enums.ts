export const FHIR_CATEGORIES = {
  IMMUNIZATIONS: { value: 'IMMUNIZATIONS', label: 'Immunizations' },
  CONDITIONS: { value: 'CONDITIONS', label: 'Conditions' },
  MEDICATIONS: { value: 'MEDICATIONS', label: 'Medications' },
  ALLERGIES: { value: 'ALLERGIES', label: 'Allergies' },
  LAB_RESULTS: { value: 'LAB_RESULTS', label: 'Lab Results' },
  PROCEDURES: { value: 'PROCEDURES', label: 'Procedures' },
} as const;

export type FhirCategory = keyof typeof FHIR_CATEGORIES;

export const FHIR_CATEGORY_LIST = Object.values(FHIR_CATEGORIES);
