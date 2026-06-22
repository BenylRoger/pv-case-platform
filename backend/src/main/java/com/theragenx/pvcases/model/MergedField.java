package com.theragenx.pvcases.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A field in the merged case view, annotated with diff status.
 *
 * Status values:
 *   - "unchanged"  : follow-up and stored value are identical
 *   - "overridden" : follow-up changed this field; previous_value is populated
 *   - "new"        : field was not present in the stored version
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MergedField {

    private String value;
    private Double confidence;
    private String source;

    /**
     * Diff status: unchanged | overridden | new
     */
    private String status;

    /**
     * Only present when status == "overridden".
     * Contains the value from the stored (pre-follow-up) version.
     */
    @JsonProperty("previous_value")
    private String previousValue;
}
