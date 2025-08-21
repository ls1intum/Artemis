package de.tum.cit.aet.artemis.jenkins.connector.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.jenkins.connector.service.JenkinsBuildService;

/**
 * REST controller for Jenkins CI build operations.
 * Provides stateless CI operations - all Jenkins state is managed internally.
 */
@RestController
@RequestMapping("/api/builds")
public class BuildController {

    private final JenkinsBuildService jenkinsBuildService;

    public BuildController(JenkinsBuildService jenkinsBuildService) {
        this.jenkinsBuildService = jenkinsBuildService;
    }

    /**
     * Triggers a build for the given participation.
     * This is a stateless operation - the connector will create/configure
     * build plans as needed and trigger the build.
     *
     * @param request the build trigger request with all necessary information
     * @return the build UUID that Artemis should store for tracking
     */
    @PostMapping("/trigger")
    public ResponseEntity<UUID> triggerBuild(@Valid @RequestBody BuildTriggerRequestDTO request) {
        UUID buildId = jenkinsBuildService.triggerBuild(request);
        return ResponseEntity.ok(buildId);
    }

    /**
     * Gets the build status for a specific build.
     *
     * @param buildId the build UUID returned from trigger
     * @return the current build status, or 404 if build doesn't exist
     */
    @GetMapping("/status/{buildId}")
    public ResponseEntity<BuildStatusResponseDTO> getBuildStatus(@PathVariable UUID buildId) {
        BuildStatusResponseDTO status = jenkinsBuildService.getBuildStatus(buildId);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    /**
     * Gets a default build script template for a programming language.
     *
     * @param language the programming language
     * @param exerciseType optional exercise type (default: "basic")
     * @return the build script template, or 404 if not supported
     */
    @GetMapping("/template/{language}")
    public ResponseEntity<String> getBuildScriptTemplate(
            @PathVariable String language,
            @RequestParam(defaultValue = "basic") String exerciseType) {
        // TODO: Implement template retrieval logic
        return ResponseEntity.notFound().build();
    }
}