package de.tum.cit.aet.artemis.metis.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseConfigDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisTutorialGroupDTO;
import de.tum.cit.aet.artemis.metis.service.MetisExerciseConfigService;

/**
 * REST resource for the Metis settings of a programming exercise: the configuration of the comprehension check, the windows of the tutorial groups, and the
 * tutorial groups of the course to choose them from. Tutors may read the configuration, which the exercise pages show; only editors may change the settings.
 * <p>
 * The reads count as automatic calls for the usage analysis: the detail page of every programming exercise reads the configuration, and the edit form loads all
 * three for its settings section, whether or not anybody uses the check. Saving the settings counts as use.
 */
@Conditional(MetisEnabled.class)
@Lazy
@FeatureUsage(UserFeature.METIS_CHECK_MANAGEMENT)
@RestController
@RequestMapping("api/metis/")
public class MetisExerciseConfigResource {

    private static final Logger log = LoggerFactory.getLogger(MetisExerciseConfigResource.class);

    private final MetisExerciseConfigService exerciseConfigService;

    private final AuthorizationCheckService authorizationCheckService;

    public MetisExerciseConfigResource(MetisExerciseConfigService exerciseConfigService, AuthorizationCheckService authorizationCheckService) {
        this.exerciseConfigService = exerciseConfigService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET exercises/{exerciseId}/config : the Metis configuration of an exercise, which every programming exercise owns from the moment it is stored. An exercise
     * without one, a programming exercise whose creation failed half-way or an exercise of another type, gets the defaults, switched off; nothing is written.
     *
     * @param exerciseId the exercise
     * @return 200 with the stored configuration, or the defaults when the exercise has none
     */
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("exercises/{exerciseId}/config")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<MetisExerciseConfigDTO> getConfig(@PathVariable long exerciseId) {
        log.debug("REST request to get the Metis configuration of exercise {}", exerciseId);
        return ResponseEntity.ok(exerciseConfigService.getConfig(exerciseId));
    }

    /**
     * PUT exercises/{exerciseId}/config : saves the Metis configuration of an exercise and moves the existing checks the change affects.
     *
     * @param exerciseId the exercise, which the body must name as well
     * @param config     the configuration the exercise should have
     * @return 200 with the saved configuration; 400 if a field is invalid, the fields contradict each other, the check is switched on for an exercise that cannot
     *         have one, or the exercise is no programming exercise; 404 if the exercise is deleted while the request runs; 409 if the body names another exercise
     *         or the rating scale changes after a student has started the check
     */
    @PutMapping("exercises/{exerciseId}/config")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<MetisExerciseConfigDTO> updateConfig(@PathVariable long exerciseId, @Valid @RequestBody MetisExerciseConfigDTO config) {
        log.debug("REST request to update the Metis configuration of exercise {}", exerciseId);
        authorizationCheckService.checkGivenExerciseIdSameForExerciseRequestBodyIdElseThrow(exerciseId, config.exerciseId());
        return ResponseEntity.ok(exerciseConfigService.updateConfig(exerciseId, config));
    }

    /**
     * GET exercises/{exerciseId}/group-windows : the windows of the tutorial groups of an exercise, which override the window of its configuration for the
     * students of each group.
     *
     * @param exerciseId the exercise
     * @return 200 with the windows in the order they were created
     */
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("exercises/{exerciseId}/group-windows")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<List<MetisGroupWindowDTO>> getGroupWindows(@PathVariable long exerciseId) {
        log.debug("REST request to get the Metis group windows of exercise {}", exerciseId);
        return ResponseEntity.ok(exerciseConfigService.getGroupWindows(exerciseId));
    }

    /**
     * PUT exercises/{exerciseId}/group-windows : replaces the windows of the tutorial groups of an exercise, keyed by the tutorial group. The {@code id} and
     * {@code exerciseId} of the windows in the body are ignored.
     *
     * @param exerciseId the exercise
     * @param windows    every window the exercise should have afterwards
     * @return 200 with the stored windows; 400 if a window is invalid, a group appears twice or belongs to another course, or the exercise is no programming
     *         exercise of a course; 409 if the windows were changed by another request at the same time
     */
    @PutMapping("exercises/{exerciseId}/group-windows")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<List<MetisGroupWindowDTO>> updateGroupWindows(@PathVariable long exerciseId, @RequestBody List<@NotNull @Valid MetisGroupWindowDTO> windows) {
        log.debug("REST request to replace the Metis group windows of exercise {}", exerciseId);
        return ResponseEntity.ok(exerciseConfigService.replaceGroupWindows(exerciseId, windows));
    }

    /**
     * GET courses/{courseId}/tutorial-groups : the tutorial groups of a course, for which an editor can set a window.
     *
     * @param courseId the course
     * @return 200 with the groups, ordered by title
     */
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("courses/{courseId}/tutorial-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<MetisTutorialGroupDTO>> getTutorialGroups(@PathVariable long courseId) {
        log.debug("REST request to get the tutorial groups of course {} for the Metis group windows", courseId);
        return ResponseEntity.ok(exerciseConfigService.getTutorialGroups(courseId));
    }
}
