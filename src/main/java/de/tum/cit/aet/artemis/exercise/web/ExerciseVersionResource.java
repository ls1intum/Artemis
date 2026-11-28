package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.web.util.PaginationUtil;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseVersionMetadataDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * REST controller for managing ExerciseVersion.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.EXERCISE_VERSION_HISTORY)
@RestController
@RequestMapping("api/exercise/")
public class ExerciseVersionResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseVersionRepository exerciseVersionRepository;

    public ExerciseVersionResource(ExerciseVersionRepository exerciseVersionRepository) {
        this.exerciseVersionRepository = exerciseVersionRepository;
    }

    /**
     * GET exercises/:exerciseId/versions : get all versions for an exercise.
     * Returns paginated exercise version history with author information.
     *
     * @param exerciseId the ID of the exercise to retrieve versions for
     * @param pageable   pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of exercise versions in body
     */
    @GetMapping("exercises/{exerciseId}/versions")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "exerciseId")
    @ApiResponse(responseCode = "200", description = "A page of exercise versions, newest first", useReturnTypeSchema = true, headers = {
            @Header(name = HttpHeaders.LINK, description = "Pagination links for the current page", schema = @Schema(type = "string")),
            @Header(name = "X-Total-Count", description = "Total number of versions across all pages", schema = @Schema(type = "integer", format = "int64")) })
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Exercise is inaccessible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(type = "object")))
    public ResponseEntity<List<ExerciseVersionMetadataDTO>> getExerciseVersions(@PathVariable Long exerciseId, @ParameterObject Pageable pageable) {
        log.debug("REST request to get versions for Exercise : {}", exerciseId);
        Page<ExerciseVersionMetadataDTO> versions = exerciseVersionRepository.findAllByExerciseIdOrderByCreatedDateDesc(exerciseId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), versions);
        return new ResponseEntity<>(versions.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET exercises/:exerciseId/versions/:versionId : get a specific version of an exercise.
     *
     * @param exerciseId the ID of the exercise that the endpoint should access
     * @param versionId  the ID of the exercise version to retrieve
     * @return the ResponseEntity with status 200 (OK) and the exercise snapshot in body
     */
    @GetMapping("exercises/{exerciseId}/versions/{versionId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "exerciseId")
    @ApiResponse(responseCode = "200", description = "The stored exercise snapshot", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Version belongs to another exercise. The empty response includes Artemis failure-alert headers.", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Exercise is inaccessible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(type = "object")))
    @ApiResponse(responseCode = "404", description = "Version was not found", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(type = "object")))
    public ResponseEntity<ExerciseSnapshotDTO> getExerciseSnapshot(@PathVariable Long exerciseId, @PathVariable Long versionId) {
        log.debug("REST request to get snapshot for ExerciseVersion : {}", versionId);
        ExerciseVersion version = exerciseVersionRepository.findByIdElseThrow(versionId);
        if (!version.getExerciseId().equals(exerciseId)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "exerciseVersion", "exerciseVersionIdMismatch",
                    "The requested exercise version does not belong to the provided exercise ID.")).build();
        }
        return ResponseEntity.ok(version.getExerciseSnapshot());
    }

}
