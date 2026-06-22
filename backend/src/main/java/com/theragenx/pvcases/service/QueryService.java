package com.theragenx.pvcases.service;

import com.theragenx.pvcases.dto.QueryRequest;
import com.theragenx.pvcases.exception.CaseNotFoundException;
import com.theragenx.pvcases.model.ReviewQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Manages reviewer queries in memory.
 * Queries are keyed by case ID for fast per-case lookup.
 * CopyOnWriteArrayList makes the list safe for concurrent reads during iteration.
 */
@Slf4j
@Service
public class QueryService {

    // caseId → ordered list of queries for that case
    private final Map<String, List<ReviewQuery>> queryStore = new ConcurrentHashMap<>();

    private final CaseService caseService;

    public QueryService(CaseService caseService) {
        this.caseService = caseService;
    }

    /**
     * Creates a new query for a specific field on a case.
     * Validates the case exists before persisting the query.
     */
    public ReviewQuery createQuery(QueryRequest request) {
        // Validate case exists — throws 404 if not
        caseService.getCase(request.getCaseId());

        ReviewQuery query = ReviewQuery.builder()
                .id(UUID.randomUUID().toString())
                .caseId(request.getCaseId())
                .fieldPath(request.getFieldPath())
                .question(request.getQuestion())
                .createdAt(Instant.now().toString())
                .status("open")
                .build();

        queryStore
                .computeIfAbsent(request.getCaseId(), k -> new CopyOnWriteArrayList<>())
                .add(query);

        log.info("Query {} created for case {} on field {}", query.getId(), query.getCaseId(), query.getFieldPath());
        return query;
    }

    /**
     * Lists all queries for a given case, ordered by creation time (insertion order).
     * Returns empty list if no queries exist — never 404, callers decide how to handle.
     */
    public List<ReviewQuery> listQueries(String caseId) {
        // Validate case exists — callers should get 404 for unknown cases
        caseService.getCase(caseId);
        return queryStore.getOrDefault(caseId, List.of());
    }
}
