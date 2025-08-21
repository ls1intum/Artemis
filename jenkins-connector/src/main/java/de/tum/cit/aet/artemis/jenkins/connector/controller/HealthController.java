package de.tum.cit.aet.artemis.jenkins.connector.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check controller for the Jenkins connector.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Gets the health status of the Jenkins connector and underlying Jenkins instance.
     *
     * @return health status with additional information
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        // TODO: Implement actual health check for Jenkins
        Map<String, Object> health = Map.of(
            "status", "UP",
            "connector", "jenkins",
            "version", "1.0.0"
        );
        return ResponseEntity.ok(health);
    }
}