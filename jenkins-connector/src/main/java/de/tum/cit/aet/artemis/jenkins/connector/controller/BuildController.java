package de.tum.cit.aet.artemis.jenkins.connector.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO.BuildStatus;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.jenkins.connector.service.JenkinsBuildService;

/**
 * REST controller for managing Jenkins builds.
 * Provides endpoints for triggering builds and checking their status.
 */
@RestController
@RequestMapping("/api/v1")
public class BuildController {

    private static final Logger log = LoggerFactory.getLogger(BuildController.class);

    private final JenkinsBuildService jenkinsBuildService;

    public BuildController(JenkinsBuildService jenkinsBuildService) {
        this.jenkinsBuildService = jenkinsBuildService;
    }

    /**
     * Triggers a new build based on the provided build request.
     *
     * @param buildRequest the build trigger request containing all necessary information
     * @return the UUID of the triggered build
     */
    @PostMapping("/build")
    public ResponseEntity<BuildTriggerResponse> triggerBuild(@Valid @RequestBody BuildTriggerRequestDTO buildRequest) {
        log.info("Received build trigger request for exercise {} and participation {}", 
                buildRequest.exerciseId(), buildRequest.participationId());

        try {
            UUID buildId = jenkinsBuildService.triggerBuild(buildRequest);
            
            BuildTriggerResponse response = new BuildTriggerResponse(buildId.toString(), "Build triggered successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to trigger build", e);
            BuildTriggerResponse errorResponse = new BuildTriggerResponse(null, "Failed to trigger build: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Gets the status of a build by its UUID.
     *
     * @param buildId the UUID of the build
     * @return the build status response
     */
    @GetMapping("/build/{buildId}")
    public ResponseEntity<BuildStatusResponseDTO> getBuildStatus(@PathVariable String buildId) {
        log.debug("Received build status request for build ID: {}", buildId);

        try {
            UUID uuid = UUID.fromString(buildId);
            BuildStatus status = jenkinsBuildService.getBuildStatus(uuid);
            
            if (status == null) {
                BuildStatusResponseDTO response = new BuildStatusResponseDTO(buildId, null, "Build not found");
                return ResponseEntity.notFound().build();
            }
            
            BuildStatusResponseDTO response = new BuildStatusResponseDTO(buildId, status, null);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid build ID format: {}", buildId);
            BuildStatusResponseDTO errorResponse = new BuildStatusResponseDTO(buildId, null, "Invalid build ID format");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Failed to get build status for ID: {}", buildId, e);
            BuildStatusResponseDTO errorResponse = new BuildStatusResponseDTO(buildId, null, "Failed to get build status");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Response record for build trigger requests.
     */
    public record BuildTriggerResponse(String buildId, String message) {
    }
}