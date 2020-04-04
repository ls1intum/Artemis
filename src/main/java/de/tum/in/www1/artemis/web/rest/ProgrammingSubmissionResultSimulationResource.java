package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@RestController
@RequestMapping("/api")
@Transactional
public class ProgrammingSubmissionResultSimulationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final UserService userService;

    private final ProgrammingExerciseResource programmingExerciseResource;

    private final ParticipationRepository participationRepository;

    private final ParticipationService participationService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final WebsocketMessagingService messagingService;

    public ProgrammingSubmissionResultSimulationResource(ProgrammingSubmissionService programmingSubmissionService, UserService userService,
            ProgrammingExerciseResource programmingExerciseResource, ParticipationRepository participationRepository, ParticipationService participationService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ResultRepository resultRepository, WebsocketMessagingService messagingService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.userService = userService;
        this.programmingExerciseResource = programmingExerciseResource;
        this.participationRepository = participationRepository;
        this.participationService = participationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.messagingService = messagingService;
    }

    /**
     * POST /programming-submissions/:participationId : Notify the application about a new push to the VCS for the participation with Id participationId This API is invoked by the
     * VCS Server at the push of a new commit
     *
     * @param
     * @param exerciseID the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was already notified about
     */
    @PostMapping(value = "courses/{userID}/no-local-setup/{exerciseID}")
    public ResponseEntity<?> notifyPush(@PathVariable Long userID, @PathVariable Long exerciseID) {
        log.debug("REST request to inform about new commit+push for participation");

        ProgrammingSubmission programmingSubmission = createSubmission(exerciseID);

        programmingSubmissionService.notifyUserAboutSubmission(programmingSubmission);

        // TODO: we should not really return status code other than 200, because Bitbucket might kill the webhook, if there are too many errors
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * This method is used by the CI system to inform Artemis about a new programming exercise build result.
     * It will make sure to:
     * - Create a result from the build result including its feedbacks
     * - Assign the result to an existing submission OR create a new submission if needed
     * - Update the result's score based on the exercise's test cases (weights, etc.)
     * - Update the exercise's test cases if the build is from a solution participation
     *
     *
     * @return a ResponseEntity to the CI system
     */
    @PostMapping(value = "courses/result/no-local-setup/{exerciseID}")
    public ResponseEntity<?> notifyNewProgrammingExerciseResult(@PathVariable Long exerciseID) {
        log.debug("Received result notify (NEW)");

        User user = userService.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ResponseEntity<ProgrammingExercise> programmingExercise = programmingExerciseResource.getProgrammingExercise(exerciseID);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise.getBody(), participant);
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get();
        Result result = createResult(programmingExerciseStudentParticipation);

        log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
        messagingService.broadcastNewResult((Participation) optionalStudentParticipation.get(), result); // TODO: can we avoid to invoke this code for non LTI students? (to improve
                                                                                                         // performance) // if (participation.isLti()) { // }
        // handles new results and sends them to LTI consumers
        /*
         * if (participation instanceof ProgrammingExerciseStudentParticipation) { ltiService.onNewResult((ProgrammingExerciseStudentParticipation) participation); }
         */
        log.info("The new result for {} was saved successfully", ((ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get()).getBuildPlanId());
        return ResponseEntity.ok().build();
    }

    private ProgrammingExerciseStudentParticipation createParticipation(ProgrammingExercise programmingExercise, Participant participant, User user) {
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setBuildPlanId(programmingExercise.getProjectKey() + "-" + user.getLogin());
        programmingExerciseStudentParticipation.setParticipant(participant);
        programmingExerciseStudentParticipation.setInitializationState(InitializationState.INITIALIZED);
        programmingExerciseStudentParticipation.setRepositoryUrl("http://" + user.getLogin() + "@localhost7990/scm/" + programmingExercise.getProjectKey() + "/"
                + programmingExercise.getProjectKey().toLowerCase() + "-" + user.getLogin() + ".git");
        programmingExerciseStudentParticipation.setInitializationDate(ZonedDateTime.now());
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);
        participationRepository.save(programmingExerciseStudentParticipation);
        return programmingExerciseStudentParticipation;
    }

    ProgrammingSubmission createSubmission(Long exerciseID) {
        User user = userService.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;
        ResponseEntity<ProgrammingExercise> programmingExercise = programmingExerciseResource.getProgrammingExercise(exerciseID);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise.getBody(), participant);
        if (optionalStudentParticipation.isEmpty()) {
            programmingExerciseStudentParticipation = createParticipation(programmingExercise.getBody(), participant, user);
        }
        else {
            programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get();
            ;
        }

        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        Random random = new Random();
        programmingSubmission.setCommitHash(String.valueOf(random.nextInt(100000)));
        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL);
        programmingExerciseStudentParticipation.addSubmissions(programmingSubmission);

        programmingSubmissionRepository.save(programmingSubmission);
        return programmingSubmission;
    }

    private Result createResult(ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation) {
        Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository
                .findFirstByParticipationIdOrderBySubmissionDateDesc(programmingExerciseStudentParticipation.getId());
        Result result = new Result();
        result.setSubmission(programmingSubmission.get());
        result.setParticipation(programmingExerciseStudentParticipation);
        result.setRated(true);
        result.resultString("7 of 13 passed");
        result.score(54L);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);
        return result;
    }

}
