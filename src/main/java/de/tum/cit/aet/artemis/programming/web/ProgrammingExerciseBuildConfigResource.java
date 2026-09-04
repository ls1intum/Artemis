package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.UpdateBuildPlanConfigurationDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseBuildPlanService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationScheduleService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

/**
 * REST controller for managing the build configuration of a programming exercise from the dedicated build plan editor.
 * <p>
 * In contrast to the full programming exercise update, this controller only updates the values the build plan editor
 * controls (build phases, Docker image, and timeout), so the build plan can be edited independently of the rest of the
 * exercise configuration.
 */
@Profile(PROFILE_LOCALCI)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseBuildConfigResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseBuildConfigResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final Optional<AutomaticAfterDueDateService> automaticAfterDueDateService;

    private final ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    private final UserRepository userRepository;

    private final ExerciseService exerciseService;

    private final ExerciseVersionService exerciseVersionService;

    public ProgrammingExerciseBuildConfigResource(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService, ProgrammingTriggerService programmingTriggerService,
            Optional<AutomaticAfterDueDateService> automaticAfterDueDateService, ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService,
            UserRepository userRepository, ExerciseService exerciseService, ExerciseVersionService exerciseVersionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseBuildPlanService = programmingExerciseBuildPlanService;
        this.programmingTriggerService = programmingTriggerService;
        this.automaticAfterDueDateService = automaticAfterDueDateService;
        this.programmingExerciseCreationScheduleService = programmingExerciseCreationScheduleService;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * PUT /programming-exercises/{exerciseId}/build-config : Updates the build plan configuration (build phases, Docker
     * image, and timeout) of an existing programming exercise from the dedicated build plan editor.
     * <p>
     * Analogous to the build plan editor for external CI systems, a template and solution build is triggered afterwards
     * so that the editor immediately shows whether the new build plan works as expected.
     *
     * @param exerciseId the id of the programming exercise whose build config should be updated
     * @param dto        the new build plan configuration and build timeout
     * @return the ResponseEntity with status 200 (OK) and the updated build config in the body
     * @throws JsonProcessingException if the build plan configuration cannot be serialized
     */
    @PutMapping("programming-exercises/{exerciseId}/build-config")
    @EnforceAtLeastEditorInExercise
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<UpdateProgrammingExerciseBuildConfigDTO> updateBuildConfig(@PathVariable long exerciseId, @Valid @RequestBody UpdateBuildPlanConfigurationDTO dto)
            throws JsonProcessingException {
        log.debug("REST request to update the build plan configuration of programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithBuildConfigElseThrow(exerciseId);
        final var user = userRepository.getUserWithAuthorities();

        // the offset has to be read before the build plan changes, so that a build and test date an instructor moved
        // manually keeps its distance to the due date instead of being reset to the default offset
        final Duration originalBuildAndTestOffset = automaticAfterDueDateService.map(service -> service.getOriginalBuildAndTestOffset(programmingExercise)).orElse(null);

        ProgrammingExerciseBuildConfig updatedBuildConfig = programmingExerciseBuildPlanService.updateBuildPlanConfiguration(programmingExercise, dto);
        updateBuildAndTestDate(programmingExercise, originalBuildAndTestOffset);

        programmingTriggerService.triggerTemplateAndSolutionBuild(exerciseId);

        // record the change like the full update path does, so it appears in the exercise version history and the audit log;
        // the version entry is also what warns a colleague who has the exercise form open (the page shows its own success message)
        exerciseService.logUpdate(programmingExercise, programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        return ResponseEntity.ok(UpdateProgrammingExerciseBuildConfigDTO.of(updatedBuildConfig));
    }

    /**
     * Recomputes the "Run Tests after Due Date" value of the exercise and reschedules its operations, because adding or
     * removing a build phase that runs after the due date changes whether such a rebuild has to be scheduled at all.
     * This mirrors what the full programming exercise update does after the build plan configuration changed.
     *
     * @param programmingExercise        the programming exercise whose build plan was updated
     * @param originalBuildAndTestOffset the offset the build and test date had before the update, so that it is preserved
     */
    private void updateBuildAndTestDate(ProgrammingExercise programmingExercise, @Nullable Duration originalBuildAndTestOffset) throws JsonProcessingException {
        if (automaticAfterDueDateService.isEmpty()) {
            return;
        }

        final ZonedDateTime computedBuildAndTestDate = automaticAfterDueDateService.orElseThrow().computeBuildAndTestDate(programmingExercise, originalBuildAndTestOffset);
        final boolean buildAndTestDateChanged = !Objects.equals(programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate(), computedBuildAndTestDate);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(computedBuildAndTestDate);
        // a build and test date and manual feedback requests are mutually exclusive, see the full programming exercise update
        boolean feedbackRequestsChanged = false;
        if (computedBuildAndTestDate != null && programmingExercise.getAllowFeedbackRequests()) {
            programmingExercise.setAllowFeedbackRequests(false);
            feedbackRequestsChanged = true;
        }

        // only persist the exercise and reschedule when the date or the feedback flag actually moved, exactly like the create/update path
        if (buildAndTestDateChanged || feedbackRequestsChanged) {
            programmingExerciseRepository.save(programmingExercise);
            programmingExerciseCreationScheduleService.scheduleOperations(programmingExercise.getId());
        }
    }
}
