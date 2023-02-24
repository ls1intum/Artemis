package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.TRIGGER_INSTRUCTOR_BUILD;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Service
public class ProgrammingTriggerService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingTriggerService.class);

    @Value("${artemis.external-system-request.batch-size}")
    private int externalSystemRequestBatchSize;

    @Value("${artemis.external-system-request.batch-waiting-time}")
    private int externalSystemRequestBatchWaitingTime;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final AuditEventRepository auditEventRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationService participationService;

    private final ProgrammingMessagingService programmingMessagingService;

    public ProgrammingTriggerService(ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ParticipationService participationService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, AuditEventRepository auditEventRepository, ResultRepository resultRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingMessagingService programmingMessagingService) {
        this.participationService = participationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auditEventRepository = auditEventRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Executes setTestCasesChanged with testCasesChanged = true, also triggers template and solution build.
     * This method should be used if the solution participation would otherwise not be built.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @throws EntityNotFoundException if there is no programming exercise for the given id.
     */
    public void setTestCasesChangedAndTriggerTestCaseUpdate(long programmingExerciseId) throws EntityNotFoundException {
        setTestCasesChanged(programmingExerciseId, true);
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(programmingExerciseId).get();

        try {
            continuousIntegrationService.get().triggerBuild(programmingExercise.getSolutionParticipation());
            continuousIntegrationService.get().triggerBuild(programmingExercise.getTemplateParticipation());
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Could not trigger build for solution repository after test case update for programming exercise with id {}", programmingExerciseId);
        }
    }

    /**
     * see the description below
     *
     * @param programmingExerciseId id of a ProgrammingExercise.
     * @param testCasesChanged      set to true to mark the programming exercise as dirty.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    public void setTestCasesChanged(long programmingExerciseId, boolean testCasesChanged) throws EntityNotFoundException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        setTestCasesChanged(programmingExercise, testCasesChanged);
    }

    /**
     * If testCasesChanged = true, this marks the programming exercise as dirty, meaning that its test cases were changed and the student submissions should be built & tested.
     * This method also sends out a notification to the client if testCasesChanged = true.
     * In case the testCaseChanged value is the same for the programming exercise or the programming exercise is not released or has no results, the method will return immediately.
     *
     * @param programmingExercise a ProgrammingExercise.
     * @param testCasesChanged    set to true to mark the programming exercise as dirty.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    private void setTestCasesChanged(ProgrammingExercise programmingExercise, boolean testCasesChanged) throws EntityNotFoundException {

        // If the flag testCasesChanged has not changed, we can stop the execution
        // Also, if the programming exercise has no results yet, there is no point in setting test cases changed to *true*.
        // It is only relevant when there are student submissions that should get an updated result.

        boolean resultsExist = resultRepository.existsByParticipation_ExerciseId(programmingExercise.getId());

        if (testCasesChanged == programmingExercise.getTestCasesChanged() || (!resultsExist && testCasesChanged)) {
            return;
        }
        programmingExercise.setTestCasesChanged(testCasesChanged);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        // Send a websocket message about the new state to the client.
        programmingMessagingService.notifyUserAboutTestCaseChanged(testCasesChanged, updatedProgrammingExercise);
    }

    /**
     * Trigger the CI of all student participations and the template participation of the given exercise.
     *
     * <p>
     * The method is async because it would time out a calling resource method.
     *
     * @param exerciseId to identify the programming exercise.
     * @throws EntityNotFoundException if there is no programming exercise for the given exercise id.
     */
    @Async
    public void triggerInstructorBuildForExercise(Long exerciseId) throws EntityNotFoundException {
        // Async can't access the authentication object. We need to do any security checks before this point.
        SecurityUtils.setAuthorizationObject();
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        // Let the instructor know that a build run was triggered.
        programmingMessagingService.notifyInstructorAboutStartedExerciseBuildRun(programmingExercise);
        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>(
                programmingExerciseStudentParticipationRepository.findWithSubmissionsByExerciseId(exerciseId));

        triggerBuildForParticipations(participations);

        // When the instructor build was triggered for the programming exercise, it is not considered 'dirty' anymore.
        setTestCasesChanged(programmingExercise, false);
        // Let the instructor know that the build run is finished.
        programmingMessagingService.notifyInstructorAboutCompletedExerciseBuildRun(programmingExercise);
    }

    /**
     * trigger the build using the batch size approach for all participations
     *
     * @param participations the participations for which the method triggerBuild should be executed.
     */
    public void triggerBuildForParticipations(List<ProgrammingExerciseStudentParticipation> participations) {
        var index = 0;
        for (var participation : participations) {
            // Execute requests in batches instead all at once.
            if (index > 0 && index % externalSystemRequestBatchSize == 0) {
                try {
                    log.info("Sleep for {}s during triggerBuild", externalSystemRequestBatchWaitingTime / 1000);
                    Thread.sleep(externalSystemRequestBatchWaitingTime);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before executing successive build for participation {}", participation.getId(), ex);
                }
            }
            triggerBuild(participation);
            index++;
        }
    }

    public void logTriggerInstructorBuild(User user, Exercise exercise, Course course) {
        var auditEvent = new AuditEvent(user.getLogin(), TRIGGER_INSTRUCTOR_BUILD, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} triggered an instructor build for all participations in exercise {} with id {}", user.getLogin(), exercise.getTitle(), exercise.getId());
    }

    private ProgrammingSubmission createSubmissionWithCommitHashAndSubmissionType(ProgrammingExerciseParticipation participation, String commitHash,
            SubmissionType submissionType) {
        // Make sure that the new submission has the submission date of now
        ProgrammingSubmission newSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash(commitHash).submitted(true).submissionDate(ZonedDateTime.now())
                .type(submissionType);
        newSubmission.setParticipation((Participation) participation);
        return programmingSubmissionRepository.saveAndFlush(newSubmission);
    }

    /**
     * Trigger a CI build for each submission & notify each user of the participation
     * Note: Instead of triggering all builds at the same time, we execute the builds in batches to not overload the CIS system (this has to be handled in the invoking method)
     * <p>
     * Note: This call "resumes the exercise", i.e. re-creates the build plan if the build plan was already cleaned before
     *
     * @param participation the participation for which we create a new submission and new result
     */
    public void triggerBuild(ProgrammingExerciseStudentParticipation participation) {
        Optional<ProgrammingSubmission> submission = participation.findLatestSubmission();
        // we only need to trigger the build if the student actually already made a submission, otherwise this is not needed
        if (submission.isPresent()) {
            try {
                if (participation.getBuildPlanId() == null || !participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
                    // in this case, we first have to resume the exercise: this includes that we again set up the build plan properly before we trigger it
                    participationService.resumeProgrammingExercise(participation);
                    // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
                }
                continuousIntegrationService.get().triggerBuild(participation);
                // TODO: this is a workaround, in the future we should use the participation to notify the client and avoid using the submission
                programmingMessagingService.notifyUserAboutSubmission(submission.get());
            }
            catch (Exception e) {
                log.error("Trigger build failed for {} with the exception {}", participation.getBuildPlanId(), e.getMessage());
                BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), participation.getId());
                programmingMessagingService.notifyUserAboutSubmissionError(participation, error);
            }
        }
    }

    /**
     * Triggers a build on the CI system and sends a websocket message to the user about the new submission and
     * Will send an error object in the case that the communication with the CI failed.
     * <p>
     * Note: This call "resumes the exercise", i.e. re-creates the build plan if the build plan was already cleaned before
     *
     * @param submission ProgrammingSubmission that was just created.
     */
    public void triggerBuildAndNotifyUser(ProgrammingSubmission submission) {
        var programmingExerciseParticipation = (ProgrammingExerciseParticipation) submission.getParticipation();
        try {
            if (programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation && (programmingExerciseParticipation.getBuildPlanId() == null
                    || !programmingExerciseParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {

                // in this case, we first have to resume the exercise: this includes that we again setup the build plan properly before we trigger it
                participationService.resumeProgrammingExercise((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation);
                // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
            }
            continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
            programmingMessagingService.notifyUserAboutSubmission(submission);
        }
        catch (Exception e) {
            log.error("Trigger build failed for {} with the exception {}", programmingExerciseParticipation.getBuildPlanId(), e.getMessage());
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            programmingMessagingService.notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Trigger the template repository build with the given commitHash.
     *
     * @param programmingExerciseId is used to retrieve the template participation.
     * @param commitHash            the unique hash code of the git repository identifying the submission, will be used for the created submission.
     * @param submissionType        will be used for the created submission.
     * @throws EntityNotFoundException if the programming exercise has no template participation (edge case).
     */
    public void triggerTemplateBuildAndNotifyUser(long programmingExerciseId, String commitHash, SubmissionType submissionType) throws EntityNotFoundException {
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExerciseParticipationService
                .findTemplateParticipationByProgrammingExerciseId(programmingExerciseId);
        // If for some reason the programming exercise does not have a template participation, we can only log and abort.
        createSubmissionTriggerBuildAndNotifyUser(templateParticipation, commitHash, submissionType);
    }

    /**
     * Creates a submission with the given type and commitHash for the provided participation.
     * Will notify the user about occurring errors when trying to trigger the build.
     *
     * @param participation  for which to create the submission.
     * @param commitHash     the unique hash code of the git repository identifying the submission,to assign to the submission.
     * @param submissionType to assign to the submission.
     */
    private void createSubmissionTriggerBuildAndNotifyUser(ProgrammingExerciseParticipation participation, String commitHash, SubmissionType submissionType) {
        ProgrammingSubmission submission = createSubmissionWithCommitHashAndSubmissionType(participation, commitHash, submissionType);
        try {
            continuousIntegrationService.get().triggerBuild((ProgrammingExerciseParticipation) submission.getParticipation());
            programmingMessagingService.notifyUserAboutSubmission(submission);
        }
        catch (Exception e) {
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            programmingMessagingService.notifyUserAboutSubmissionError(submission, error);
        }
    }
}
