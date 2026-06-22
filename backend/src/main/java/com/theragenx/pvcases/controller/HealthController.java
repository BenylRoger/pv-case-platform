package com.theragenx.pvcases.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Simple liveness endpoint for Docker healthchecks and on-call verification.
 * Returns a flat JSON object — easy to parse with jq or grep in shell scripts.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "pv-cases",
                "timestamp", Instant.now().toString()
        ));
    }
}
