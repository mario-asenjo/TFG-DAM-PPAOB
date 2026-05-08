package com.ppaob.backend.adapters.in.rest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
/**
 * Exposes lightweight health information for API and database reachability.
 */
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a health controller with JDBC access.
     *
     * @param jdbcTemplate template used to execute the database liveness probe
     */
    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns health status for the backend service and primary database.
     *
     * @return map with service name, status, database flag and response timestamp
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Integer dbValue = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Map.of(
                "service", "backend-api",
                "status", "UP",
                "database", dbValue != null && dbValue == 1 ? "UP" : "DOWN",
                "timestamp", Instant.now().toString()
        );
    }
}
