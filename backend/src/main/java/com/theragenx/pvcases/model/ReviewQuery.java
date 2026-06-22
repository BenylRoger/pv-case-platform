package com.theragenx.pvcases.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A reviewer query raised against a specific field in a case.
 * Used to flag discrepancies or request clarification before sign-off.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQuery {

    private String id;

    @JsonProperty("case_id")
    private String caseId;

    /**
     * Dot-separated path to the queried field.
     * Convention: "sections.patient.age" or "sections.adverse_event.onset_date"
     */
    @JsonProperty("field_path")
    private String fieldPath;

    private String question;

    @JsonProperty("created_at")
    private String createdAt;

    /**
     * Query lifecycle status. Starts as "open".
     * Future states could include "resolved", "escalated".
     */
    private String status;
}
