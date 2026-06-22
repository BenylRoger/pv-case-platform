package com.theragenx.pvcases.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Raw case document as extracted by the AI from source PDFs.
 * Used for bootstrap loading and follow-up ingestion.
 * Sections are a two-level map: section name → field name → FieldValue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaseDocument {

    @JsonProperty("case_id")
    private String caseId;

    private Integer version;

    @JsonProperty("case_classification")
    private String caseClassification;

    @JsonProperty("extracted_at")
    private String extractedAt;

    @JsonProperty("source_document")
    private String sourceDocument;

    /**
     * Structured field data grouped by section (patient, suspect_drug, etc.)
     * Each field within a section is a FieldValue with value/confidence/source.
     */
    private Map<String, Map<String, FieldValue>> sections;

    /**
     * Fields the AI could not extract from the follow-up document.
     * Only populated on follow-up payloads; null/empty on initial versions.
     */
    @JsonProperty("missing_fields")
    private List<String> missingFields;
}
