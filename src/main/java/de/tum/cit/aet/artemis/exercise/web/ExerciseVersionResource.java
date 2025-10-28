package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseVersionMetadataDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing ExerciseVersion.
 */
@Profile(PROFILE_CORE)
@Lazy
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
     * GET versions/:exerciseId/versions : get all versions for an exercise.
     * Returns paginated exercise version history with author information.
     *
     * @param exerciseId the ID of the exercise to retrieve versions for
     * @param pageable   pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of exercise versions in body
     */
    @GetMapping("{exerciseId}/versions")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "exerciseId")
    public ResponseEntity<List<ExerciseVersionMetadataDTO>> getExerciseVersions(@PathVariable Long exerciseId, Pageable pageable) {
        log.debug("REST request to get versions for Exercise : {}", exerciseId);
        Page<ExerciseVersionMetadataDTO> versions = exerciseVersionRepository.findAllByExerciseIdOrderByCreatedDateDesc(exerciseId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), versions);
        return new ResponseEntity<>(versions.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET version/:versionId : get a specific version of an exercise.
     *
     * @param versionId the ID of the exercise version to retrieve
     * @return the ResponseEntity with status 200 (OK) and the exercise snapshot in body
     */
    @GetMapping("{exerciseId}/version/{versionId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "exerciseId")
    public ResponseEntity<ExerciseSnapshotDTO> getExerciseSnapshot(@PathVariable Long exerciseId, @PathVariable Long versionId) {
        log.debug("REST request to get snapshot for ExerciseVersion : {}", versionId);
        ExerciseVersion version = exerciseVersionRepository.findByIdElseThrow(versionId);
        if (!version.getExerciseId().equals(exerciseId)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "exerciseVersion", "quizAlreadyVisible", "Quiz is already visible to students.")).build();
        }
        return ResponseEntity.ok(version.getExerciseSnapshot());
    }

}
