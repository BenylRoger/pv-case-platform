package com.theragenx.pvcases.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * The fully merged and annotated case view returned by GET /cases/{id}
 * and POST /cases/{id}/follow-ups.
 *
 * Every field carries a diff status so the reviewer can immediately see
 * what changed, what was confirmed, and what is brand new.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MergedCase {

    @JsonProperty("case_id")
    private String caseId;

    private Integer version;

    @JsonProperty("case_classification")
    private String caseClassification;

    @JsonProperty("merged_at")
    private String mergedAt;

    @JsonProperty("source_document")
    private String sourceDocument;

    /**
     * Fields the AI explicitly flagged as unextractable in the follow-up.
     * Surfaced here so reviewers know they need to fill these manually.
     */
    @JsonProperty("missing_fields")
    private List<String> missingFields;

    /**
     * Sections with annotated fields. Structure mirrors CaseDocument.sections
     * but each field is a MergedField with status annotation.
     */
    private Map<String, Map<String, MergedField>> sections;
}
