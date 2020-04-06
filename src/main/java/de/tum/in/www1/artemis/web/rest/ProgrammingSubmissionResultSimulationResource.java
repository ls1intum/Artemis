package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.service.*;

@RestController
@RequestMapping("/api")
@Transactional
public class ProgrammingSubmissionResultSimulationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final UserService userService;

    private final ParticipationRepository participationRepository;

    private final ParticipationService participationService;

    private final WebsocketMessagingService messagingService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService;

    public ProgrammingSubmissionResultSimulationResource(ProgrammingSubmissionService programmingSubmissionService, UserService userService,
            ParticipationRepository participationRepository, ParticipationService participationService, WebsocketMessagingService messagingService,
            ProgrammingExerciseService programmingExerciseService, ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.userService = userService;
        this.participationRepository = participationRepository;
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
     * @return HTTP OK
     */

    @PostMapping(value = "submissions/no-local-setup/{exerciseID}")
    public ResponseEntity<?> notifyAboutParticipationAndSubmissionSimulation(@PathVariable Long exerciseID) {

        ProgrammingSubmission programmingSubmission = programmingSubmissionResultSimulationService.createSubmission(exerciseID);

        programmingSubmissionService.notifyUserAboutSubmission(programmingSubmission);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * This method is used to notify artemis that there is a new programming exercise build result.
     * This result is only a SIMULATION for the testing of programming exercises without local setup
     *
     * @param exerciseID id of the exercise
     * @return HTTP OK
     */
    @PostMapping(value = "results/no-local-setup/{exerciseID}")
    public ResponseEntity<?> notifyNewProgrammingExerciseResult(@PathVariable Long exerciseID) {
        log.debug("Received result notify (NEW)");
        User user = userService.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseID);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise, participant);
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get();
        Result result = programmingSubmissionResultSimulationService.createResult(programmingExerciseStudentParticipation);

        messagingService.broadcastNewResult((Participation) optionalStudentParticipation.get(), result);
        // TODO: can we avoid to invoke this code for non LTI students? (to improve performance) // if (participation.isLti()) { // }
        // handles new results and sends them to LTI consumers
        /*
         * if (participation instanceof ProgrammingExerciseStudentParticipation) { ltiService.onNewResult((ProgrammingExerciseStudentParticipation) participation); }
         */
        log.info("The new result for {} was saved successfully", ((ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get()).getBuildPlanId());
        return ResponseEntity.status(HttpStatus.OK).build();
    }

}
