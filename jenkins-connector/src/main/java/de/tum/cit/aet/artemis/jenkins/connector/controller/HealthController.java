package de.tum.cit.aet.artemis.jenkins.connector.controller;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.jenkins.connector.service.JenkinsEndpoints;

/**
 * REST controller for health checks.
 * Provides endpoints for checking the health of the Jenkins connector service.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final RestTemplate restTemplate;

    @Value("${jenkins.url}")
    private URI jenkinsServerUri;

    public HealthController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Health check endpoint that verifies the service and Jenkins connectivity.
     *
     * @return health status information
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        log.debug("Health check requested");

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("service", "jenkins-connector");
        details.put("version", "1.0.0");
        details.put("jenkinsUrl", jenkinsServerUri.toString());

        boolean isHealthy = true;
        String status = "UP";

        // Check Jenkins connectivity
        try {
            URI uri = JenkinsEndpoints.HEALTH.buildEndpoint(jenkinsServerUri).build(true).toUri();
            restTemplate.getForObject(uri, String.class);
            details.put("jenkinsConnection", "UP");
        } catch (Exception e) {
            log.warn("Jenkins health check failed", e);
            details.put("jenkinsConnection", "DOWN");
            details.put("jenkinsError", e.getMessage());
            isHealthy = false;
            status = "DEGRADED";
        }

        // Add database health check here if needed
        details.put("database", "UP"); // Assuming database is healthy for now

        HealthResponse response = new HealthResponse(status, details);
        
        if (isHealthy) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }

    /**
     * Response record for health check requests.
     */
    public record HealthResponse(String status, Map<String, Object> details) {
    }
}