package com.theragenx.pvcases.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theragenx.pvcases.exception.CaseNotFoundException;
import com.theragenx.pvcases.model.CaseDocument;
import com.theragenx.pvcases.model.FieldValue;
import com.theragenx.pvcases.model.MergedCase;
import com.theragenx.pvcases.model.MergedField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service managing case storage and the follow-up merge algorithm.
 *
 * Storage model (in-memory, intentionally simple):
 *   - caseStore     : caseId → latest CaseDocument (raw fields, used as merge baseline)
 *   - mergedStore   : caseId → latest MergedCase  (annotated view, served to clients)
 *
 * Merge decisions:
 *   - Field in follow-up, same value as stored     → status: "unchanged"
 *   - Field in follow-up, different value           → status: "overridden", previous_value populated
 *   - Field in follow-up, not in stored             → status: "new"
 *   - Field in stored, absent from follow-up        → retained as "unchanged"
 *     Rationale: follow-up AI extraction is selective; absence does not imply removal.
 *     The stored value remains authoritative until explicitly overridden.
 */
@Slf4j
@Service
public class CaseService {

    private static final String BOOTSTRAP_FILE = "data/case_v1.json";

    private final Map<String, CaseDocument> caseStore = new ConcurrentHashMap<>();
    private final Map<String, MergedCase> mergedStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public CaseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Bootstrap: load case_v1.json from classpath into in-memory storage.
     * Converts to MergedCase with all fields marked "unchanged" (baseline state).
     */
    @PostConstruct
    public void bootstrap() {
        try {
            InputStream is = new ClassPathResource(BOOTSTRAP_FILE).getInputStream();
            CaseDocument doc = objectMapper.readValue(is, CaseDocument.class);
            caseStore.put(doc.getCaseId(), doc);
            mergedStore.put(doc.getCaseId(), toBaselineMergedCase(doc));
            log.info("Bootstrapped case {} (version {})", doc.getCaseId(), doc.getVersion());
        } catch (Exception e) {
            throw new RuntimeException("Failed to bootstrap case from " + BOOTSTRAP_FILE, e);
        }
    }

    /**
     * Returns the latest MergedCase for a given case ID.
     * Always returns the annotated view (diff-aware) so callers get consistent shape.
     */
    public MergedCase getCase(String caseId) {
        MergedCase mc = mergedStore.get(caseId);
        if (mc == null) {
            throw new CaseNotFoundException("Case not found: " + caseId);
        }
        return mc;
    }

    /**
     * Applies a follow-up payload to the stored case.
     * Returns the merged result with per-field diff annotations.
     * Also persists the updated case as the new baseline for future merges.
     */
    public MergedCase applyFollowUp(String caseId, CaseDocument followUp) {
        CaseDocument stored = caseStore.get(caseId);
        if (stored == null) {
            throw new CaseNotFoundException("Case not found: " + caseId);
        }

        MergedCase merged = merge(stored, followUp);

        // Persist: update raw store so the next follow-up merges against this version
        CaseDocument updated = buildUpdatedDocument(stored, followUp, merged);
        caseStore.put(caseId, updated);
        mergedStore.put(caseId, merged);

        log.info("Applied follow-up to case {} → version {}", caseId, merged.getVersion());
        return merged;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Merge algorithm
    // ──────────────────────────────────────────────────────────────────────────

    MergedCase merge(CaseDocument stored, CaseDocument followUp) {
        Map<String, Map<String, MergedField>> mergedSections = new LinkedHashMap<>();

        // Union of section keys from both documents
        Set<String> allSections = new LinkedHashSet<>();
        if (stored.getSections() != null) allSections.addAll(stored.getSections().keySet());
        if (followUp.getSections() != null) allSections.addAll(followUp.getSections().keySet());

        for (String section : allSections) {
            Map<String, FieldValue> storedFields = getSectionOrEmpty(stored, section);
            Map<String, FieldValue> followUpFields = getSectionOrEmpty(followUp, section);

            Map<String, MergedField> mergedFields = new LinkedHashMap<>();

            // Union of field keys within this section
            Set<String> allFields = new LinkedHashSet<>(storedFields.keySet());
            allFields.addAll(followUpFields.keySet());

            for (String field : allFields) {
                FieldValue storedField = storedFields.get(field);
                FieldValue followUpField = followUpFields.get(field);
                mergedFields.put(field, diffField(storedField, followUpField));
            }

            mergedSections.put(section, mergedFields);
        }

        return MergedCase.builder()
                .caseId(stored.getCaseId())
                .version(stored.getVersion() + 1)
                .caseClassification(
                        followUp.getCaseClassification() != null
                                ? followUp.getCaseClassification()
                                : stored.getCaseClassification()
                )
                .mergedAt(Instant.now().toString())
                .sourceDocument(followUp.getSourceDocument() != null
                        ? followUp.getSourceDocument()
                        : stored.getSourceDocument())
                .missingFields(
                        followUp.getMissingFields() != null
                                ? followUp.getMissingFields()
                                : Collections.emptyList()
                )
                .sections(mergedSections)
                .build();
    }

    /**
     * Determines the diff status for a single field.
     *
     * Four cases:
     *   stored=null,  followUp=present  → NEW
     *   stored=present, followUp=null   → UNCHANGED (retain stored; absence ≠ deletion)
     *   stored=present, followUp=present, same value  → UNCHANGED
     *   stored=present, followUp=present, diff value  → OVERRIDDEN
     */
    MergedField diffField(FieldValue stored, FieldValue followUp) {
        if (stored == null && followUp != null) {
            // Entirely new field introduced by follow-up
            return MergedField.builder()
                    .value(followUp.getValue())
                    .confidence(followUp.getConfidence())
                    .source(followUp.getSource())
                    .status("new")
                    .build();
        }

        if (stored != null && followUp == null) {
            // Follow-up AI didn't touch this field — keep stored value, mark unchanged
            return MergedField.builder()
                    .value(stored.getValue())
                    .confidence(stored.getConfidence())
                    .source(stored.getSource())
                    .status("unchanged")
                    .build();
        }

        // Both present — compare values
        if (Objects.equals(stored.getValue(), followUp.getValue())) {
            return MergedField.builder()
                    .value(followUp.getValue())
                    .confidence(followUp.getConfidence())
                    .source(followUp.getSource())
                    .status("unchanged")
                    .build();
        } else {
            return MergedField.builder()
                    .value(followUp.getValue())
                    .confidence(followUp.getConfidence())
                    .source(followUp.getSource())
                    .status("overridden")
                    .previousValue(stored.getValue())
                    .build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, FieldValue> getSectionOrEmpty(CaseDocument doc, String section) {
        if (doc.getSections() == null) return Collections.emptyMap();
        return doc.getSections().getOrDefault(section, Collections.emptyMap());
    }

    /**
     * Builds an updated CaseDocument from the merged result so it can serve as
     * the raw baseline for any subsequent follow-ups.
     */
    private CaseDocument buildUpdatedDocument(CaseDocument stored, CaseDocument followUp, MergedCase merged) {
        // Flatten MergedCase back to CaseDocument (raw FieldValues, no status)
        Map<String, Map<String, FieldValue>> updatedSections = new LinkedHashMap<>();
        merged.getSections().forEach((section, fields) -> {
            Map<String, FieldValue> rawFields = new LinkedHashMap<>();
            fields.forEach((fieldName, mf) -> rawFields.put(fieldName,
                    FieldValue.builder()
                            .value(mf.getValue())
                            .confidence(mf.getConfidence())
                            .source(mf.getSource())
                            .build()
            ));
            updatedSections.put(section, rawFields);
        });

        return CaseDocument.builder()
                .caseId(stored.getCaseId())
                .version(merged.getVersion())
                .caseClassification(merged.getCaseClassification())
                .extractedAt(merged.getMergedAt())
                .sourceDocument(merged.getSourceDocument())
                .sections(updatedSections)
                .missingFields(null)
                .build();
    }

    /**
     * Converts an initial CaseDocument to a MergedCase for the GET endpoint.
     * All fields are marked "unchanged" (baseline — no prior version to diff against).
     */
    private MergedCase toBaselineMergedCase(CaseDocument doc) {
        Map<String, Map<String, MergedField>> sections = new LinkedHashMap<>();
        if (doc.getSections() != null) {
            doc.getSections().forEach((sectionName, fields) -> {
                Map<String, MergedField> mergedFields = new LinkedHashMap<>();
                fields.forEach((fieldName, fv) -> mergedFields.put(fieldName,
                        MergedField.builder()
                                .value(fv.getValue())
                                .confidence(fv.getConfidence())
                                .source(fv.getSource())
                                .status("unchanged")
                                .build()
                ));
                sections.put(sectionName, mergedFields);
            });
        }

        return MergedCase.builder()
                .caseId(doc.getCaseId())
                .version(doc.getVersion())
                .caseClassification(doc.getCaseClassification())
                .mergedAt(doc.getExtractedAt())
                .sourceDocument(doc.getSourceDocument())
                .missingFields(Collections.emptyList())
                .sections(sections)
                .build();
    }
}
