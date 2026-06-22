package com.theragenx.pvcases.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /queries.
 * All three fields are required — a query without all context is not actionable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    @NotBlank(message = "caseId is required")
    @JsonProperty("case_id")
    private String caseId;

    @NotBlank(message = "fieldPath is required")
    @JsonProperty("field_path")
    private String fieldPath;

    @NotBlank(message = "question is required")
    private String question;
}
