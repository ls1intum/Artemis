package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionResultSimulationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Only for local development
 * Simulates submission and results for a programming exercise without a connection to the VCS and CI server
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Profile("dev")
@RestController
@RequestMapping(ProgrammingSubmissionResultSimulationResource.Endpoints.ROOT)
public class ProgrammingSubmissionResultSimulationResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final UserRepository userRepository;

    private final ParticipationService participationService;

    private final WebsocketMessagingService messagingService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService;

    private final ExerciseRepository exerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public ProgrammingSubmissionResultSimulationResource(ProgrammingSubmissionService programmingSubmissionService, UserRepository userRepository,
            ParticipationService participationService, WebsocketMessagingService messagingService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingSubmissionResultSimulationService programmingSubmissionResultSimulationService, ExerciseRepository exerciseRepository,
            AuthorizationCheckService authCheckService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.userRepository = userRepository;
        this.participationService = participationService;
        this.messagingService = messagingService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionResultSimulationService = programmingSubmissionResultSimulationService;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * This method is used to create a participation and a submission
     * This participation and submission are only SIMULATIONS for the testing
     * of programming exercises without a connection to the VCS and CI server
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param exerciseId the id of the exercise
     * @return HTTP OK and ProgrammingSubmission
     */

    @PostMapping(Endpoints.SUBMISSIONS_SIMULATION)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingSubmission> createParticipationAndSubmissionSimulation(@PathVariable Long exerciseId) {

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!authCheckService.isAtLeastEditorForExercise(exercise, user)) {
            return forbidden();
        }

        ProgrammingSubmission programmingSubmission = programmingSubmissionResultSimulationService.createSubmission(exerciseId);

        programmingSubmissionService.notifyUserAboutSubmission(programmingSubmission);

        try {
            return ResponseEntity.created(new URI("/api/submissions" + programmingSubmission.getId())).body(programmingSubmission);
        }
        catch (URISyntaxException e) {
            log.error("Error while simulating a submission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while simulating a submission: " + e.getMessage(), "errorSubmission")).body(null);
        }
    }

    /**
     * This method is used to notify artemis that there is a new programming exercise build result.
     * This result is only a SIMULATION for the testing of programming exercises without a connection
     * to the VCS and CI server
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param exerciseId id of the exercise
     * @return HTTP OK and Result
     */
    @PostMapping(Endpoints.RESULTS_SIMULATION)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Result> createNewProgrammingExerciseResult(@PathVariable Long exerciseId) {
        log.debug("Received result notify (NEW)");
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exerciseId);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise, user);

        if (optionalStudentParticipation.isEmpty()) {
            return forbidden();
        }

        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get();
        Result result = programmingSubmissionResultSimulationService.createResult(programmingExerciseStudentParticipation);

        messagingService.broadcastNewResult((Participation) optionalStudentParticipation.get(), result);
        log.info("The new result for {} was saved successfully", ((ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get()).getBuildPlanId());
        try {
            return ResponseEntity.created(new URI("/api/results" + result.getId())).body(result);
        }
        catch (URISyntaxException e) {
            log.error("Error while simulating a result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while simulating a result: " + e.getMessage(), "errorResult")).body(null);
        }
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String SUBMISSIONS_SIMULATION = "/exercises/{exerciseId}/submissions/no-vcs-and-ci-available";

        public static final String RESULTS_SIMULATION = "/exercises/{exerciseId}/results/no-vcs-and-ci-available";

        private Endpoints() {
        }
    }

}
