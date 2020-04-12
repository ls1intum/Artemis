package de.tum.in.www1.artemis.web.rest;

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

    public ProgrammingSubmissionResultSimulationResource(ProgrammingSubmissionService programmingSubmissionService, UserService userService,
            ParticipationService participationService, WebsocketMessagingService messagingService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.userService = userService;
        this.participationService = participationService;
        this.messagingService = messagingService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingSubmissionResultSimulationService = programmingSubmissionResultSimulationService;
    }

    /**
     * This method is used to create a participation and a submission
     * This participation and submission are only SIMULATIONS for the testing
     * of programming exercises without local setup
     *
     * @param exerciseID the id of the exercise
     * @return HTTP OK and ProgrammingSubmission
     */

    @PostMapping(value = "submissions/no-local-setup/{exerciseID}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingSubmission> createParticipationAndSubmissionSimulation(@PathVariable Long exerciseID) {

        ProgrammingSubmission programmingSubmission = programmingSubmissionResultSimulationService.createSubmission(exerciseID);

        programmingSubmissionService.notifyUserAboutSubmission(programmingSubmission);

        return ResponseEntity.ok().body(programmingSubmission);
    }

    /**
     * This method is used to notify artemis that there is a new programming exercise build result.
     * This result is only a SIMULATION for the testing of programming exercises without local setup
     *
     * @param exerciseID id of the exercise
     * @return HTTP OK and Result
     */
    @PostMapping(value = "results/no-local-setup/{exerciseID}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> createNewProgrammingExerciseResult(@PathVariable Long exerciseID) {
        log.debug("Received result notify (NEW)");
        User user = userService.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseID);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise, participant);
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
