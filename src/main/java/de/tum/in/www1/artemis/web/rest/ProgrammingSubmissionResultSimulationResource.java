package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionResultSimulationService;

@Profile("dev")
@RestController
@RequestMapping("/api")
public class ProgrammingSubmissionResultSimulationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final UserService userService;

    private final ParticipationService participationService;

    private final WebsocketMessagingService messagingService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    public ProgrammingSubmissionResultSimulationResource(ProgrammingSubmissionService programmingSubmissionService, UserService userService,
            ParticipationService participationService, WebsocketMessagingService messagingService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService, ExerciseService exerciseService,
            AuthorizationCheckService authCheckService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.userService = userService;
        this.participationService = participationService;
        this.messagingService = messagingService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingSubmissionResultSimulationService = programmingSubmissionResultSimulationService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
    }

    /**
     * This method is used to create a participation and a submission
     * This participation and submission are only SIMULATIONS for the testing
     * of programming exercises without local setup
     *
     * @param exerciseId the id of the exercise
     * @return HTTP OK and ProgrammingSubmission
     */

    @PostMapping(value = "submissions/no-local-setup/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingSubmission> createParticipationAndSubmissionSimulation(@PathVariable Long exerciseId) {

        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            return forbidden();
        }

        ProgrammingSubmission programmingSubmission = programmingSubmissionResultSimulationService.createSubmission(exerciseId);

        programmingSubmissionService.notifyUserAboutSubmission(programmingSubmission);

        return ResponseEntity.ok().body(programmingSubmission);
    }

    /**
     * This method is used to notify artemis that there is a new programming exercise build result.
     * This result is only a SIMULATION for the testing of programming exercises without local setup
     *
     * @param exerciseId id of the exercise
     * @return HTTP OK and Result
     */
    @PostMapping(value = "results/no-local-setup/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> createNewProgrammingExerciseResult(@PathVariable Long exerciseId) {
        log.debug("Received result notify (NEW)");
        User user = userService.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseId);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise, participant);

        if (optionalStudentParticipation.isEmpty()) {
            return forbidden();
        }

        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get();
        Result result = programmingSubmissionResultSimulationService.createResult(programmingExerciseStudentParticipation);

        messagingService.broadcastNewResult((Participation) optionalStudentParticipation.get(), result);
        log.info("The new result for {} was saved successfully", ((ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get()).getBuildPlanId());
        return ResponseEntity.ok().body(result);
    }

    public static final class Endpoints {

        public static final String SUBMISSIONS_SIMULATION = "/submissions/no-local-setup";

        public static final String RESULTS_SIMULATION = "/results/no-local-setup";

    }

}
