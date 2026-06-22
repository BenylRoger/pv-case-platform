package com.theragenx.pvcases.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theragenx.pvcases.exception.CaseNotFoundException;
import com.theragenx.pvcases.model.CaseDocument;
import com.theragenx.pvcases.model.FieldValue;
import com.theragenx.pvcases.model.MergedCase;
import com.theragenx.pvcases.model.MergedField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the core merge algorithm in CaseService.
 * The service itself is tested in isolation — no Spring context needed.
 */
class CaseMergeServiceTest {

    private CaseService caseService;

    @BeforeEach
    void setUp() {
        // Use real ObjectMapper — bootstrap will run from classpath resource
        caseService = new CaseService(new ObjectMapper());
        caseService.bootstrap();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 1: Identical values → all fields "unchanged"
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Follow-up with identical values marks all fields as unchanged")
    void merge_identicalValues_allUnchanged() {
        CaseDocument stored = buildCase(Map.of(
                "patient", Map.of(
                        "initials", fv("M.K.", 0.98, "p.2 §1"),
                        "age", fv("62", 0.91, "p.2 §1")
                )
        ));

        CaseDocument followUp = buildCase(Map.of(
                "patient", Map.of(
                        "initials", fv("M.K.", 0.98, "p.2 §1"),
                        "age", fv("62", 0.91, "p.2 §1")
                )
        ));

        MergedCase result = caseService.merge(stored, followUp);

        assertThat(result.getSections().get("patient").get("initials").getStatus()).isEqualTo("unchanged");
        assertThat(result.getSections().get("patient").get("age").getStatus()).isEqualTo("unchanged");
        assertThat(result.getSections().get("patient").get("initials").getPreviousValue()).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2: Changed value → "overridden" with previous_value populated
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Follow-up with changed value marks field as overridden with previous value")
    void merge_changedValue_overriddenWithPreviousValue() {
        CaseDocument stored = buildCase(Map.of(
                "adverse_event", Map.of(
                        "onset_date", fv("2026-03-12", 0.72, "p.4 §2")
                )
        ));

        CaseDocument followUp = buildCase(Map.of(
                "adverse_event", Map.of(
                        "onset_date", fv("2026-03-15", 0.88, "p.4 §3")  // date corrected in follow-up
                )
        ));

        MergedCase result = caseService.merge(stored, followUp);
        MergedField field = result.getSections().get("adverse_event").get("onset_date");

        assertThat(field.getStatus()).isEqualTo("overridden");
        assertThat(field.getValue()).isEqualTo("2026-03-15");
        assertThat(field.getPreviousValue()).isEqualTo("2026-03-12");
        assertThat(field.getConfidence()).isEqualTo(0.88);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3: Field only in follow-up → "new"
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Field present in follow-up but not in stored is marked as new")
    void merge_fieldOnlyInFollowUp_markedNew() {
        CaseDocument stored = buildCase(Map.of(
                "patient", Map.of(
                        "initials", fv("M.K.", 0.98, "p.2 §1")
                )
        ));

        CaseDocument followUp = buildCase(Map.of(
                "patient", Map.of(
                        "initials", fv("M.K.", 0.98, "p.2 §1"),
                        "dob", fv("1964-05-10", 0.85, "p.2 §2")  // new field extracted in follow-up
                )
        ));

        MergedCase result = caseService.merge(stored, followUp);
        MergedField dobField = result.getSections().get("patient").get("dob");

        assertThat(dobField.getStatus()).isEqualTo("new");
        assertThat(dobField.getValue()).isEqualTo("1964-05-10");
        assertThat(dobField.getPreviousValue()).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 4: Field in stored but absent from follow-up → retained as "unchanged"
    // Design decision: follow-up absence ≠ deletion; stored value is authoritative
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Field in stored but absent from follow-up is retained as unchanged")
    void merge_fieldAbsentFromFollowUp_retainedUnchanged() {
        CaseDocument stored = buildCase(Map.of(
                "suspect_drug", Map.of(
                        "drug_name", fv("Atorvastatin", 0.97, "p.3 §3"),
                        "batch_number", fv("BN-2024-001", 0.90, "p.3 §5")  // AI couldn't find this in follow-up
                )
        ));

        CaseDocument followUp = buildCase(Map.of(
                "suspect_drug", Map.of(
                        "drug_name", fv("Atorvastatin", 0.97, "p.3 §3")
                        // batch_number not present in follow-up
                )
        ));

        MergedCase result = caseService.merge(stored, followUp);
        MergedField batchField = result.getSections().get("suspect_drug").get("batch_number");

        assertThat(batchField).isNotNull();
        assertThat(batchField.getStatus()).isEqualTo("unchanged");
        assertThat(batchField.getValue()).isEqualTo("BN-2024-001");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5: missing_fields array is surfaced in the merged response
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("missing_fields from follow-up payload is surfaced in merged response")
    void merge_missingFields_surfacedInResponse() {
        CaseDocument stored = buildCase(Map.of(
                "patient", Map.of("initials", fv("M.K.", 0.98, "p.2 §1"))
        ));

        CaseDocument followUp = buildCase(Map.of(
                "patient", Map.of("initials", fv("M.K.", 0.98, "p.2 §1"))
        ));
        followUp.setMissingFields(List.of("sections.patient.dob", "sections.reporter.contact"));

        MergedCase result = caseService.merge(stored, followUp);

        assertThat(result.getMissingFields())
                .containsExactly("sections.patient.dob", "sections.reporter.contact");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 6: getCase with unknown ID throws 404
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCase with unknown caseId throws CaseNotFoundException")
    void getCase_unknownId_throws404() {
        assertThatThrownBy(() -> caseService.getCase("DOES-NOT-EXIST"))
                .isInstanceOf(CaseNotFoundException.class)
                .hasMessageContaining("DOES-NOT-EXIST");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private CaseDocument buildCase(Map<String, Map<String, FieldValue>> sections) {
        return CaseDocument.builder()
                .caseId("TEST-001")
                .version(1)
                .caseClassification("non-significant")
                .sections(sections)
                .build();
    }

    private FieldValue fv(String value, double confidence, String source) {
        return FieldValue.builder()
                .value(value)
                .confidence(confidence)
                .source(source)
                .build();
    }
}
