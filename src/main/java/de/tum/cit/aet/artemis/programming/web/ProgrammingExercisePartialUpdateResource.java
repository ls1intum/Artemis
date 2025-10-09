package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

/**
 * REST controller for updating partial information of a programming exercise such as the timeline or the problem statement.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExercisePartialUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExercisePartialUpdateResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    public ProgrammingExercisePartialUpdateResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, ExerciseService exerciseService, ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
    }

    /**
     * PUT /programming-exercises/timeline : Updates the timeline attributes of a given exercise
     *
     * @param updatedProgrammingExercise containing the changes that have to be saved
     * @param notificationText           an optional text to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) with the updated ProgrammingExercise, or with status 403 (Forbidden)
     *         if the user is not allowed to update the exercise or with 404 (Not Found) if the updated ProgrammingExercise couldn't be found in the database
     */
    @PutMapping("programming-exercises/timeline")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExerciseTimeline(@RequestBody ProgrammingExercise updatedProgrammingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update the timeline of ProgrammingExercise : {}", updatedProgrammingExercise);
        var existingProgrammingExercise = programmingExerciseRepository.findByIdElseThrow(updatedProgrammingExercise.getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, existingProgrammingExercise, user);
        updatedProgrammingExercise = programmingExerciseCreationUpdateService.updateTimeline(updatedProgrammingExercise, notificationText);
        exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);
    }

    /**
     * PATCH /programming-exercises-problem: Updates the problem statement of the exercise.
     *
     * @param exerciseId              The ID of the exercise for which to change the problem statement
     * @param updatedProblemStatement The new problemStatement
     * @param notificationText        to notify the student group about the updated problemStatement on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated problemStatement, with status 404 if the programmingExercise could not be found, or with 403 if the
     *         user does not have permissions to access the programming exercise.
     */
    @PatchMapping("programming-exercises/{exerciseId}/problem-statement")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> updateProblemStatement(@PathVariable long exerciseId, @RequestBody String updatedProblemStatement,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise with new problem statement: {}", updatedProblemStatement);
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", exerciseId));
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        var updatedProgrammingExercise = programmingExerciseCreationUpdateService.updateProblemStatement(programmingExercise, updatedProblemStatement, notificationText);
        exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        // we saved a problem statement with test ids instead of test names. For easier editing we send a problem statement with test names to the client:
        programmingExerciseTaskService.replaceTestIdsWithNames(updatedProgrammingExercise);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);
    }

}
