package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.TRIGGER_INSTRUCTOR_BUILD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localci.service.ci.SharedBuildTriggerData;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.ParticipationBuildTriggerDTO;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingTriggerService.class);

    @Value("${artemis.external-system-request.batch-size}")
    private int externalSystemRequestBatchSize;

    @Value("${artemis.external-system-request.batch-waiting-time}")
    private int externalSystemRequestBatchWaitingTime;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

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
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, AuditEventRepository auditEventRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingMessagingService programmingMessagingService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService, ProgrammingSubmissionMessagingService programmingSubmissionMessagingService) {
        this.participationService = participationService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auditEventRepository = auditEventRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingMessagingService = programmingMessagingService;
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
    public void triggerInstructorBuildForExercise(long exerciseId) throws EntityNotFoundException {
        // The caller's context reaches this thread when there is one; this stands in for the paths that have none.
        // Security checks still belong before this point, since the stand-in is not a user.
        SecurityUtils.setAuthorizationObject();
        // Loaded with the associations the trigger reads off the exercise, so the batch below does not have to load it
        // a second time and no participation has to load either of them for itself.
        var programmingExercise = programmingExerciseRepository.findWithBuildConfigAndAuxiliaryRepositoriesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("ProgrammingExercise", exerciseId));

        // Let the instructor know that a build run was triggered.
        programmingMessagingService.notifyInstructorAboutStartedExerciseBuildRun(programmingExercise);
        var triggerData = programmingExerciseStudentParticipationRepository.findBuildTriggerDataByExerciseId(exerciseId);
        triggerBuildForParticipationData(triggerData, programmingExercise);

        // When the instructor build was triggered for the programming exercise, it is not considered 'dirty' anymore.
        // Deliberately by id: that call saves the exercise, and the exercise loaded above carries its auxiliary
        // repositories, a collection with orphan removal. Merging it after a run that can take minutes would delete an
        // auxiliary repository added in the meantime, so the flag is flipped on a freshly read exercise instead.
        programmingExerciseTestCaseChangedService.setTestCasesChanged(exerciseId, false);
        // Let the instructor know that the build run is finished.
        programmingMessagingService.notifyInstructorAboutCompletedExerciseBuildRun(programmingExercise);
    }

    /**
     * Triggers a build for every given participation of one exercise, pacing the batch the same way as the entity based
     * path above.
     * <p>
     * This is the path an instructor's "build all" and the build-and-test-after-due-date schedule take. Its input is a
     * projection rather than participation entities, so an exercise with a thousand participations costs one query for
     * the trigger inputs instead of a thousand and two, and the database ships roughly a third of the bytes. See
     * {@link ParticipationBuildTriggerDTO}.
     *
     * @param triggerData what a trigger reads off each participation of the exercise, newest submission included
     * @param exercise    the exercise those participations belong to, loaded with its build config and auxiliary
     *                        repositories
     */
    public void triggerBuildForParticipationData(List<ParticipationBuildTriggerDTO> triggerData, ProgrammingExercise exercise) {
        if (triggerData.isEmpty()) {
            return;
        }
        // Everything a trigger reads off the exercise rather than off the participation is resolved once for the batch:
        // the build config, the auxiliary repositories, the build statistics and the head commit of the test
        // repository are the same for every participation.
        SharedBuildTriggerData sharedData = prepareSharedTriggerDataOrNone(exercise);
        int index = 0;
        for (var participationData : triggerData) {
            var participation = participationFor(participationData, exercise);
            if (participation == null) {
                continue;
            }
            pauseBetweenBatches(index, participationData.participationId());
            triggerBuild(participation, sharedData);
            index++;
        }
    }

    /**
     * Returns the participation to hand to the trigger.
     * <p>
     * A participation that has to be resumed is written back to the database, so for those the real entity is loaded.
     * Every other participation is only read from, and the projection already holds every field the trigger and the
     * websocket notification look at, so it is turned into a detached participation instead of being loaded again. That
     * object is never passed to a repository.
     *
     * @param participationData what a trigger reads off the participation
     * @param exercise          the exercise of the participation, already loaded with its build config and auxiliary
     *                              repositories
     * @return the participation to trigger, or null if it has to be resumed but no longer exists
     */
    @Nullable
    private ProgrammingExerciseStudentParticipation participationFor(ParticipationBuildTriggerDTO participationData, ProgrammingExercise exercise) {
        if (participationData.needsResume()) {
            var participation = programmingExerciseStudentParticipationRepository.findWithSubmissionsById(participationData.participationId()).orElse(null);
            if (participation == null) {
                log.warn("Not triggering participation {}: it no longer exists", participationData.participationId());
                return null;
            }
            participation.setProgrammingExercise(exercise);
            return participation;
        }
        return participationData.toDetachedParticipation(exercise);
    }

    /**
     * Pauses before starting the next batch of requests to the external build system, so that triggering many
     * participations does not fill the build queue in one go.
     *
     * @param index           how many participations of this batch were already triggered
     * @param participationId the participation that is about to be triggered, for the log message on interruption
     */
    private void pauseBetweenBatches(int index, long participationId) {
        if (index == 0 || index % externalSystemRequestBatchSize != 0) {
            return;
        }
        try {
            log.info("Sleep for {}s during triggerBuild", externalSystemRequestBatchWaitingTime / 1000);
            Thread.sleep(externalSystemRequestBatchWaitingTime);
        }
        catch (InterruptedException ex) {
            log.error("Exception encountered when pausing before executing successive build for participation {}", participationId, ex);
        }
    }

    /**
     * trigger the build using the batch size approach for all participations
     *
     * @param participations the participations for which the method triggerBuild should be executed.
     */
    public void triggerBuildForParticipations(Collection<ProgrammingExerciseStudentParticipation> participations) {
        triggerBuildForParticipations(participations, null);
    }

    /**
     * Trigger the build for all given participations, reusing an exercise the caller already loaded.
     *
     * @param participations the participations for which the method triggerBuild should be executed
     * @param loadedExercise the exercise of those participations, loaded with its build config and auxiliary
     *                           repositories, or null when the caller does not have it and it should be loaded here
     */
    public void triggerBuildForParticipations(Collection<ProgrammingExerciseStudentParticipation> participations, @Nullable ProgrammingExercise loadedExercise) {
        // Everything a trigger reads off the exercise rather than off the participation is resolved once per exercise
        // here, not once per student: the build config, the auxiliary repositories, the build statistics and the head
        // commit of the test repository are the same for every participation of an exercise. Triggering all
        // participations of a course of two thousand used to resolve each of them two thousand times, which is what
        // made an instructor's "build all" spike the node handling it and the database behind it. Participations are
        // grouped because callers do not always pass a single exercise: a student exam hands over that student's
        // participations across the whole exam.
        Map<Long, List<ProgrammingExerciseStudentParticipation>> participationsByExerciseId = participations.stream().filter(Objects::nonNull)
                .filter(participation -> participation.getExercise() != null).collect(Collectors.groupingBy(participation -> participation.getExercise().getId()));
        long groupedCount = participationsByExerciseId.values().stream().mapToLong(List::size).sum();
        if (groupedCount != participations.size()) {
            log.warn("Not triggering {} of {} participations: they carry no exercise", participations.size() - groupedCount, participations.size());
        }

        var index = 0;
        for (var participationsOfExercise : participationsByExerciseId.values()) {
            // A participation without a submission is not triggered at all, so an exercise where nobody submitted must
            // not pay for the shared data either: resolving it reads the test repository and the build statistics.
            List<ProgrammingExerciseStudentParticipation> triggerable = participationsOfExercise.stream().filter(participation -> participation.findLatestSubmission().isPresent())
                    .toList();
            if (triggerable.isEmpty()) {
                continue;
            }
            // Worth doing even for a single participation: one load of the exercise with its build config and auxiliary
            // repositories costs less than the separate lookups the trigger would otherwise make for each of them.
            SharedBuildTriggerData sharedData = prepareSharedTriggerDataOrNone(triggerable, loadedExercise);
            // Only the participations that will actually be built: a participation without a submission returns from
            // triggerBuild immediately, so counting it towards the external system's batch size would buy a pause for a
            // build that never happened.
            for (var participation : triggerable) {
                // Execute requests in batches when using an external build system.
                pauseBetweenBatches(index, participation.getId());
                triggerBuild(participation, sharedData);
                index++;
            }
        }
    }

    /**
     * Resolves the shared inputs, falling back to resolving nothing if that fails.
     * <p>
     * Resolving happens outside the per-participation error handling, so a failure here would otherwise abort the
     * remaining exercises of the batch and skip the notification that tells the instructor the run finished. Falling
     * back leaves every participation to resolve what it needs itself, which is what happened before the batch existed:
     * slower, but the run still completes and a failure that is specific to one participation still only affects it.
     *
     * @param participationsOfExercise the participations of one exercise that are about to be triggered
     * @param loadedExercise           an exercise the caller already loaded, or null
     * @return the shared inputs, or {@link SharedBuildTriggerData#NONE} if they could not be resolved
     */
    private SharedBuildTriggerData prepareSharedTriggerDataOrNone(List<ProgrammingExerciseStudentParticipation> participationsOfExercise,
            @Nullable ProgrammingExercise loadedExercise) {
        try {
            return prepareSharedTriggerData(participationsOfExercise, loadedExercise);
        }
        catch (Exception e) {
            log.error("Could not resolve the shared build trigger inputs for exercise {}; each participation resolves them itself",
                    participationsOfExercise.getFirst().getExercise().getId(), e);
            return SharedBuildTriggerData.NONE;
        }
    }

    /**
     * Resolves the shared inputs for an exercise the caller already loaded, falling back to resolving nothing if that
     * fails, for the same reason as the overload above.
     *
     * @param exercise the exercise whose participations are about to be triggered
     * @return the shared inputs, or {@link SharedBuildTriggerData#NONE} if they could not be resolved
     */
    private SharedBuildTriggerData prepareSharedTriggerDataOrNone(ProgrammingExercise exercise) {
        try {
            return continuousIntegrationTriggerService.map(triggerService -> triggerService.prepareSharedTriggerData(exercise)).orElse(SharedBuildTriggerData.NONE);
        }
        catch (Exception e) {
            log.error("Could not resolve the shared build trigger inputs for exercise {}; each participation resolves them itself", exercise.getId(), e);
            return SharedBuildTriggerData.NONE;
        }
    }

    /**
     * Loads the exercise of the given participations once, with the associations a trigger reads off it, and resolves
     * the trigger inputs that are the same for all of them.
     * <p>
     * The loaded exercise is set on every participation, so the trigger finds the build config and the auxiliary
     * repositories already initialized and does not query for either. Both of their loaders return the association when
     * it is already there, so one load here replaces two queries per participation. Nothing is retained between
     * batches, so there is nothing to invalidate when an instructor changes the exercise.
     *
     * @param participationsOfExercise the participations of one exercise that are about to be triggered
     * @param loadedExercise           an exercise the caller already loaded, or null
     * @return the trigger inputs shared by those participations
     */
    private SharedBuildTriggerData prepareSharedTriggerData(List<ProgrammingExerciseStudentParticipation> participationsOfExercise, @Nullable ProgrammingExercise loadedExercise) {
        if (continuousIntegrationTriggerService.isEmpty()) {
            return SharedBuildTriggerData.NONE;
        }
        long exerciseId = participationsOfExercise.getFirst().getExercise().getId();
        Optional<ProgrammingExercise> exercise = loadedExercise != null && exerciseId == loadedExercise.getId() ? Optional.of(loadedExercise)
                : programmingExerciseRepository.findWithBuildConfigAndAuxiliaryRepositoriesById(exerciseId);
        if (exercise.isEmpty()) {
            return SharedBuildTriggerData.NONE;
        }
        participationsOfExercise.forEach(participation -> participation.setProgrammingExercise(exercise.get()));
        return continuousIntegrationTriggerService.get().prepareSharedTriggerData(exercise.get());
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
        triggerBuild(participation, SharedBuildTriggerData.NONE);
    }

    /**
     * Trigger a CI build for the latest submission of the participation and notify its owner, reusing trigger inputs
     * the caller resolved for the whole exercise.
     *
     * @param participation the participation for which we create a new submission and new result
     * @param sharedData    the trigger inputs shared by every participation of the exercise
     */
    public void triggerBuild(ProgrammingExerciseStudentParticipation participation, SharedBuildTriggerData sharedData) {
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
                continuousIntegrationTriggerService.orElseThrow().triggerBuild(participation, true, sharedData);
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
