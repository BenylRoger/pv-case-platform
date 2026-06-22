package com.theragenx.pvcases.controller;

import com.theragenx.pvcases.model.CaseDocument;
import com.theragenx.pvcases.model.MergedCase;
import com.theragenx.pvcases.service.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Case endpoints.
 *
 * GET  /cases/{caseId}              - fetch latest merged case view
 * POST /cases/{caseId}/follow-ups   - apply follow-up payload, get diff-annotated merge
 */
@Slf4j
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // Allow React dev server (localhost:3000) without proxy config
public class CaseController {

    private final CaseService caseService;

    /**
     * Returns the most recent version of a case.
     * Response includes diff status on all fields (baseline = all "unchanged").
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<MergedCase> getCase(@PathVariable String caseId) {
        log.info("GET /cases/{}", caseId);
        return ResponseEntity.ok(caseService.getCase(caseId));
    }

    /**
     * Merges a follow-up payload onto the stored case.
     * Returns the full merged view with per-field diff annotations.
     *
     * The follow-up body has the same shape as a CaseDocument.
     * Only fields present in the follow-up are compared; absent fields are retained.
     */
    @PostMapping("/{caseId}/follow-ups")
    public ResponseEntity<MergedCase> applyFollowUp(
            @PathVariable String caseId,
            @RequestBody CaseDocument followUp) {

        log.info("POST /cases/{}/follow-ups", caseId);

        // If caseId in path differs from body, path wins (body may omit caseId)
        if (followUp.getCaseId() == null) {
            followUp.setCaseId(caseId);
        }

        MergedCase result = caseService.applyFollowUp(caseId, followUp);
        return ResponseEntity.ok(result);
    }
}
