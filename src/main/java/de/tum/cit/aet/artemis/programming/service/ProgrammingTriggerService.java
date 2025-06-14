package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.TRIGGER_INSTRUCTOR_BUILD;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingTriggerService.class);

    private final ProfileService profileService;

    @Value("${artemis.external-system-request.batch-size}")
    private int externalSystemRequestBatchSize;

    @Value("${artemis.external-system-request.batch-waiting-time}")
    private int externalSystemRequestBatchWaitingTime;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final AuditEventRepository auditEventRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    public ProgrammingTriggerService(ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService, ParticipationService participationService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, AuditEventRepository auditEventRepository, ResultRepository resultRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingMessagingService programmingMessagingService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProfileService profileService,
            ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService, ProgrammingSubmissionMessagingService programmingSubmissionMessagingService) {
        this.participationService = participationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auditEventRepository = auditEventRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingMessagingService = programmingMessagingService;
        this.profileService = profileService;
        this.programmingExerciseTestCaseChangedService = programmingExerciseTestCaseChangedService;
        this.programmingSubmissionMessagingService = programmingSubmissionMessagingService;
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
                programmingExerciseStudentParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(exerciseId));

        triggerBuildForParticipations(participations);

        // When the instructor build was triggered for the programming exercise, it is not considered 'dirty' anymore.
        programmingExerciseTestCaseChangedService.setTestCasesChanged(programmingExercise, false);
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
            // Execute requests in batches when using an external build system.
            if (!profileService.isLocalCIActive() && index > 0 && index % externalSystemRequestBatchSize == 0) {
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
        Optional<ProgrammingSubmission> optionalSubmission = participation.findLatestSubmission();
        // we only need to trigger the build if the student actually already made a submission, otherwise this is not needed
        if (optionalSubmission.isPresent()) {
            var submission = optionalSubmission.get();
            try {
                // Make sure the relation is set correctly to avoid issues with lazy-loading until participation is used for notifying students
                submission.setParticipation(participation);
                if (participation.getBuildPlanId() == null || !participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
                    // in this case, we first have to resume the exercise: this includes that we again set up the build plan properly before we trigger it
                    participationService.resumeProgrammingExercise(participation);
                    // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
                }
                continuousIntegrationTriggerService.orElseThrow().triggerBuild(participation, true);
                // TODO: this is a workaround, in the future we should use the participation to notify the client and avoid using the submission
                programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, participation.getProgrammingExercise().getId());
            }
            catch (Exception e) {
                log.error("Trigger build failed for {} with the exception {}", participation.getBuildPlanId(), e.getMessage());
                BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), participation.getId());
                programmingSubmissionMessagingService.notifyUserAboutSubmissionError(participation, error);
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
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExerciseParticipation);
            programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, programmingExerciseParticipation.getExercise().getId());
        }
        catch (Exception e) {
            log.error("Trigger build failed for {} with the exception {}", programmingExerciseParticipation.getBuildPlanId(), e.getMessage());
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            programmingSubmissionMessagingService.notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Trigger the template repository build with the given commitHash.
     *
     * @param programmingExerciseId is used to retrieve the template participation.
     * @param commitHash            the unique hash code of the git repository identifying the submission, will be used for the created submission.
     * @param submissionType        will be used for the created submission.
     * @param triggeredByPushTo     specifies the type of repository the push was made to.
     * @throws EntityNotFoundException if the programming exercise has no template participation (edge case).
     */
    public void triggerTemplateBuildAndNotifyUser(long programmingExerciseId, String commitHash, SubmissionType submissionType, RepositoryType triggeredByPushTo)
            throws EntityNotFoundException {
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExerciseParticipationService
                .findTemplateParticipationByProgrammingExerciseId(programmingExerciseId);
        // If for some reason the programming exercise does not have a template participation, we can only log and abort.
        createSubmissionTriggerBuildAndNotifyUser(templateParticipation, commitHash, submissionType, triggeredByPushTo);
    }

    public void triggerTemplateBuildAndNotifyUser(long programmingExerciseId, String commitHash, SubmissionType submissionType) throws EntityNotFoundException {
        triggerTemplateBuildAndNotifyUser(programmingExerciseId, commitHash, submissionType, RepositoryType.TESTS);
    }

    /**
     * Creates a submission with the given type and commitHash for the provided participation.
     * Will notify the user about occurring errors when trying to trigger the build.
     *
     * @param participation     for which to create the submission.
     * @param commitHash        the unique hash code of the git repository identifying the submission,to assign to the submission.
     * @param submissionType    to assign to the submission.
     * @param triggeredByPushTo specifies the type of repository the push was made to.
     */
    private void createSubmissionTriggerBuildAndNotifyUser(ProgrammingExerciseParticipation participation, String commitHash, SubmissionType submissionType,
            RepositoryType triggeredByPushTo) {
        ProgrammingSubmission submission = createSubmissionWithCommitHashAndSubmissionType(participation, commitHash, submissionType);
        try {
            continuousIntegrationTriggerService.orElseThrow().triggerBuild((ProgrammingExerciseParticipation) submission.getParticipation(), commitHash, triggeredByPushTo);
            programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, participation.getProgrammingExercise().getId());
        }
        catch (Exception e) {
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            programmingSubmissionMessagingService.notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Triggers a new build for the template and solution repositories.
     *
     * @param programmingExerciseId The ID of the programming exercise.
     */
    public void triggerTemplateAndSolutionBuild(final long programmingExerciseId) {
        final var templateParticipation = templateProgrammingExerciseParticipationRepository.findWithEagerSubmissionsByProgrammingExerciseId(programmingExerciseId);
        templateParticipation.ifPresent(this::triggerBuild);

        final var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findWithEagerSubmissionsByProgrammingExerciseId(programmingExerciseId);
        solutionParticipation.ifPresent(this::triggerBuild);
    }

    /**
     * Takes a participation and triggers a build for it.
     *
     * @param participation A participation. Assumes that the submissions are present.
     */
    private void triggerBuild(final ProgrammingExerciseParticipation participation) {
        final Optional<ProgrammingSubmission> submission = participation.findLatestSubmission();
        if (submission.isPresent()) {
            triggerBuildAndNotifyUser(submission.get());
        }
        else {
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(participation);
        }
    }
}
