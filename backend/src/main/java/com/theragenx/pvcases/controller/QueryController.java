package com.theragenx.pvcases.controller;

import com.theragenx.pvcases.dto.QueryRequest;
import com.theragenx.pvcases.model.ReviewQuery;
import com.theragenx.pvcases.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Query endpoints.
 *
 * POST /queries             - raise a new reviewer query
 * GET  /queries?caseId=id   - list all queries for a case
 */
@Slf4j
@RestController
@RequestMapping("/queries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QueryController {

    private final QueryService queryService;

    /**
     * Creates a new query.
     * Returns 201 Created with the persisted query including its generated ID.
     */
    @PostMapping
    public ResponseEntity<ReviewQuery> createQuery(@Valid @RequestBody QueryRequest request) {
        log.info("POST /queries for case {} field {}", request.getCaseId(), request.getFieldPath());
        ReviewQuery created = queryService.createQuery(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all queries for a case, ordered by creation time.
     * Returns empty array (not 404) when no queries exist yet.
     */
    @GetMapping
    public ResponseEntity<List<ReviewQuery>> listQueries(
            @RequestParam("caseId") String caseId) {
        log.info("GET /queries?caseId={}", caseId);
        return ResponseEntity.ok(queryService.listQueries(caseId));
    }
}
