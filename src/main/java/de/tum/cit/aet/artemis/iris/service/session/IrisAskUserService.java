package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentReviewService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Orchestrates the Iris ask-user pipeline for programming exercise chats.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisAskUserService {

    private static final Logger log = LoggerFactory.getLogger(IrisAskUserService.class);

    public static final String ASK_USER_QUIZ_FAILED_ERROR_KEY = "artemisApp.exerciseChatbot.errors.askUserQuizFailed";

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final PyrisPipelineService pyrisPipelineService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final UserRepository userRepository;

    private final IrisAssessmentReviewService irisAssessmentReviewService;

    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> quizTimers = new ConcurrentHashMap<>();

    public IrisAskUserService(IrisSettingsService irisSettingsService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            IrisChatSessionRepository irisChatSessionRepository, IrisChatSessionService irisChatSessionService, PyrisPipelineService pyrisPipelineService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, UserRepository userRepository, IrisAssessmentReviewService irisAssessmentReviewService,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.userRepository = userRepository;
        this.irisAssessmentReviewService = irisAssessmentReviewService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Sends a build-with-points event to the ask-user pipeline when a programming exercise result can trigger ask-user mode.
     *
     * @param resultEvent the result event of the submission
     */
    @EventListener
    public void handleNewResultEvent(NewResultEvent resultEvent) {
        var result = resultEvent.getEventObject();
        var participation = result.getSubmission().getParticipation();

        // Ask-user quiz is disabled for practice participation
        if (participation.isPracticeMode() || !(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation)
                || participation.getExercise().isExamExercise()) {
            return;
        }
        if (!studentParticipation.getStudent().map(User::hasOptedIntoLLMUsage).orElse(false)) {
            return;
        }
        if (!(result.getSubmission() instanceof ProgrammingSubmission latestSubmission)) {
            return;
        }
        // Abort if submission has no points
        if (!submissionHasPoints(latestSubmission)) {
            return;
        }

        var settings = irisSettingsService.getSettingsForExercise(studentParticipation.getProgrammingExercise());
        if (settings.enabled() && settings.askUserModeEnabled() && !irisChatSessionService.shouldSendProgressStalledEvent(studentParticipation)) {
            explainAskUserMode(studentParticipation, latestSubmission, settings);
        }
    }

    /**
     * Handles ask-user pipeline events returned by Pyris and updates the corresponding Iris assessment.
     *
     * @param job          the Pyris job
     * @param statusUpdate the status update from Pyris
     */
    public void handleStatusUpdate(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var pipeEvent = getAskUserEvent(statusUpdate.event());
        if (pipeEvent.isEmpty()) {
            return;
        }

        var session = irisChatSessionRepository.findByIdElseThrow(job.sessionId());
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var exercise = resolveProgrammingExerciseForAskUserSession(session);
        user.hasOptedIntoLLMUsageElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var inClassQuiz = session.isInClassQuiz();
        switch (pipeEvent.get()) {
            case USER_STARTS_QUIZ -> handleUserStartsQuiz(session);
            case QUIZ_FINISHED -> handleQuizFinished(session, user, exercise, statusUpdate, inClassQuiz);
            case NEXT_QUESTION -> handleNextQuestion(session, user, exercise, statusUpdate, inClassQuiz);
            case FIRST_QUESTION -> handleFirstQuestion(session, exercise, statusUpdate);
            default -> {
            }
        }
    }

    /**
     * Starts ask-user mode in the current programming exercise chat session.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return the current chat session
     */
    public IrisChatSession startQuizForCurrentSession(ProgrammingExercise exercise, User user) {
        return startQuizForCurrentSession(exercise, user, false);
    }

    /**
     * Starts in-class ask-user mode in the current programming exercise chat session for a student.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return the current chat session
     */
    public IrisChatSession startInClassQuizForCurrentSession(ProgrammingExercise exercise, User user) {
        irisAssessmentReviewService.validateInClassQuizIsAvailableOrElseThrow(exercise);
        return startQuizForCurrentSession(exercise, user, true);
    }

    /**
     * Checks whether the submission has a positive score.
     *
     * @param submission the submission
     * @return true if latest submission has points
     */
    private boolean submissionHasPoints(ProgrammingSubmission submission) {
        return submission.getLatestResult() != null && submission.getLatestResult().getScore() > 0;
    }

    /**
     * Checks whether the student already completed the regular or in-class quiz.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @param inClass  whether the in-class assessment should be checked
     * @return true if a verdict exists
     */
    public boolean isQuizAlreadyDone(ProgrammingExercise exercise, User user, boolean inClass) {
        return programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), inClass, false)
                .map(participation -> inClass ? participation.getIrisAssessmentInClass() : participation.getIrisAssessment()).map(assessment -> assessment.getVerdict() != null)
                .orElse(false);
    }

    /**
     * Continues the ask-user pipeline for an active ask-user-mode chat session after the user submitted a message.
     *
     * @param session the active ask-user-mode chat session
     */
    public void requestAndHandleResponse(IrisChatSession session) {
        if (!session.isInAskUserModePipeline()) {
            throw new ConflictException("Iris Ask-user Mode is not active for this session", "Iris", "irisAskUserModeInactive");
        }
        requestAndHandleResponseAskUser(session, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Validates access, creates a fresh ask-user-mode session, resets the assessment verdict/reasoning, and asynchronously
     * requests the first pipeline step from Pyris.
     *
     * @param exercise    the programming exercise
     * @param user        the student
     * @param inClassQuiz whether the quiz should run in in-class mode
     * @return the newly created chat session with ask-user mode active
     */
    private IrisChatSession startQuizForCurrentSession(ProgrammingExercise exercise, User user, boolean inClassQuiz) {
        validateAskUserAvailable(exercise, user);

        var session = getFreshAskUserSession(exercise, user);
        session.setInAskUserModePipeline(true);
        session.setInClassQuiz(inClassQuiz);
        session.setQuestionsAsked(0);
        irisChatSessionRepository.save(session);
        irisAssessmentReviewService.resetVerdictAndReasoning(user, exercise, inClassQuiz);

        CompletableFuture.runAsync(() -> requestAndHandleResponseAskUser(session, Optional.of(IrisPipeEvent.USER_STARTS_QUIZ.name()), Optional.empty(), Optional.empty()))
                .exceptionally(e -> {
                    log.error("Error while starting ask-user mode for session {}", session.getId(), e);
                    return null;
                });

        return session;
    }

    /**
     * Registers a tab-defocus event while in ask-user mode.
     *
     * @param exercise the programming exercise
     * @param user     the student
     */
    public void registerDefocusForCurrentSession(ProgrammingExercise exercise, User user) {
        validateAskUserAvailable(exercise, user);

        var session = getCurrentAskUserSession(exercise, user);
        if (!session.isInAskUserModePipeline()) {
            return;
        }

        stopTimerForSession(session);

        CompletableFuture.runAsync(() -> requestAndHandleResponseAskUser(session, Optional.of(IrisPipeEvent.TAB_DEFOCUS.name()), Optional.empty(), Optional.empty()))
                .exceptionally(e -> {
                    log.error("Error while sending tab defocus message to Iris for session {}", session.getId(), e);
                    return null;
                });
    }

    /**
     * Starts the per-question ask-user timer for the user's current ask-user-mode session.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return timer information for the current question, with null values if no timer was started
     */
    public IrisQuizTimerDTO startTimerForCurrentSession(ProgrammingExercise exercise, User user) {
        validateAskUserAvailable(exercise, user);
        var session = getCurrentAskUserSession(exercise, user);
        if (!session.isInAskUserModePipeline()) {
            return new IrisQuizTimerDTO(null, 0);
        }
        var settings = irisSettingsService.getSettingsForExercise(exercise).askUserModeSettings();
        ZonedDateTime expiresAt = ZonedDateTime.now().plusSeconds(settings.timeLimitQuestion());

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> requestAndHandleResponseAskUser(session, Optional.of(IrisPipeEvent.TIMER_RAN_OUT.name()), Optional.empty(), Optional.empty()), expiresAt.toInstant());

        quizTimers.put(session.getId(), future);

        return new IrisQuizTimerDTO(expiresAt, settings.timeLimitQuestion());
    }

    /**
     * Stops the per-question ask-user timer for the user's current ask-user-mode session.
     *
     * @param exercise the programming exercise
     * @param user     the student
     */
    public void stopTimerForCurrentSession(ProgrammingExercise exercise, User user) {
        validateAskUserAvailable(exercise, user);
        var session = getCurrentAskUserSession(exercise, user);

        stopTimerForSession(session);
    }

    /**
     * Returns whether the quiz has been started and is currently in progress.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @param inClass  whether the normal or in-class quiz is relevant
     * @return whether there is a quiz in progress
     */
    public boolean isQuizStarted(ProgrammingExercise exercise, User user, boolean inClass) {
        var session = getCurrentAskUserSession(exercise, user);
        return inClass ? session.isInAskUserModePipeline() && session.isInClassQuiz() : session.isInAskUserModePipeline() && !session.isInClassQuiz();
    }

    /**
     * Notifies the student's ask-user-mode session that a build with points was submitted. Resets a running regular quiz
     * (in-class quizzes are never interrupted) or the existing Iris assessment before asynchronously sending the
     * build-with-points event to Pyris.
     *
     * @param studentParticipation the participation whose submission triggered the event
     * @param latestSubmission     the latest submission with points
     * @param settings             the Iris course settings to use for the pipeline request
     */
    private void explainAskUserMode(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission latestSubmission, IrisCourseSettings settings) {
        var participationWithAssessment = programmingExerciseStudentParticipationRepository.findWithIrisAssessmentById(studentParticipation.getId()).orElseThrow();
        var exercise = participationWithAssessment.getProgrammingExercise();
        var student = participationWithAssessment.getStudent().orElseThrow();
        var session = getCurrentAskUserSession(exercise, student);

        // In-class quiz should not be interrupted/reset there is only one try
        if (session.isInClassQuiz()) {
            return;
        }

        if (!resetRunningRegularQuizBeforeBuildWithPoints(session)) {
            var assessment = participationWithAssessment.getIrisAssessment();
            if (assessment == null) {
                irisAssessmentReviewService.createNewAssessment(participationWithAssessment);
            }
            else {
                irisAssessmentReviewService.resetVerdictAndReasoning(assessment);
            }
        }

        CompletableFuture
                .runAsync(() -> requestAndHandleResponseAskUser(session, Optional.of(IrisPipeEvent.BUILD_WITH_POINTS.name()), Optional.of(settings), Optional.of(latestSubmission)))
                .exceptionally(e -> {
                    log.error("Error while sending build with points message to Iris for user {}", studentParticipation.getParticipant().getName(), e);
                    return null;
                });
        log.info("Sent build with points message to user {}", studentParticipation.getParticipant().getName());
    }

    /**
     * Resets an active ask-user quiz after Pyris reports a terminal failure.
     *
     * @param job the failed ask-user job
     * @return true if an active quiz was reset
     */
    public boolean resetAskUserPipelineAfterPyrisFailure(ChatJob job) {
        return job.isAskUserPipeline() && resetAskUserPipelineAfterPyrisFailure(job.sessionId(), job.jobId());
    }

    /**
     * Resolves the exercise, settings, acting user, and relevant submission for the given session and sends the ask-user
     * pipeline request to Pyris. Resets the ask-user pipeline state if the request could not be dispatched.
     *
     * @param session          the ask-user-mode chat session
     * @param event            the optional ask-user pipeline event to send, if any
     * @param settings         the Iris course settings to use, or empty to look them up for the exercise
     * @param latestSubmission the submission that triggered the event, or empty to use the latest eligible submission
     */
    private void requestAndHandleResponseAskUser(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings,
            Optional<ProgrammingSubmission> latestSubmission) {
        var exercise = resolveProgrammingExerciseForAskUserSession(session);
        var actualSettings = settings.orElseGet(() -> irisSettingsService.getSettingsForExercise(exercise));
        if (!actualSettings.enabled() || !actualSettings.askUserModeEnabled()) {
            throw new ConflictException("Iris Ask-user Mode is not enabled for this exercise", "Iris", "irisDisabled");
        }

        var actualUser = latestSubmission.flatMap(this::getStudentFromSubmission).orElseGet(() -> userRepository.findByIdElseThrow(session.getUserId()));
        var actualLatestSubmission = latestSubmission.flatMap(submission -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(submission.getId()))
                .or(() -> getLatestSubmissionWithPointsBeforeDueDateWithEagerResultsAndFeedbacksAndBuildLogsIfExists(exercise, actualUser));

        var loadedSession = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        if (!(loadedSession instanceof IrisChatSession chatSession)) {
            throw new IllegalStateException("Expected IrisChatSession for ask-user session id " + session.getId());
        }
        ensureAskUserSession(chatSession, exercise);
        var submission = actualLatestSubmission
                .orElseThrow(() -> new ConflictException("Iris Ask-user Mode requires a programming submission", "Iris", "irisAskUserModeSubmissionMissing"));
        if (!pyrisPipelineService.executeAskUserPipeline(actualSettings.variant().jsonValue(), submission, exercise, chatSession, event, actualSettings.askUserModeSettings())) {
            resetAskUserPipelineAfterPyrisFailure(chatSession.getId(), null);
        }
    }

    /**
     * Finds the user's non-practice-mode participation and returns its latest submission with points scored before the
     * exercise due date, eagerly loaded with results, feedbacks, and build logs.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return the eagerly loaded submission, or empty if no eligible participation or submission exists
     */
    private Optional<ProgrammingSubmission> getLatestSubmissionWithPointsBeforeDueDateWithEagerResultsAndFeedbacksAndBuildLogsIfExists(ProgrammingExercise exercise, User user) {
        var participations = programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin()).stream()
                .filter(p -> !p.isPracticeMode()).toList();

        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return programmingSubmissionRepository
                .findLatestSubmissionWithEagerResultsAndFeedbacksAndBuildLogsBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participations.getFirst().getId());
    }

    /**
     * Checks whether the user has a latest non-practice submission with points before the exercise due date.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return true if such a submission exists
     */
    public boolean hasLatestSubmissionWithPointsBeforeDueDateIfExists(ProgrammingExercise exercise, User user) {
        var participations = programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin()).stream()
                .filter(p -> !p.isPracticeMode()).toList();

        if (participations.isEmpty()) {
            return false;
        }
        return programmingSubmissionRepository.findLatestSubmissionIdBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participations.getLast().getId())
                .isPresent();
    }

    private Optional<User> getStudentFromSubmission(ProgrammingSubmission submission) {
        if (submission.getParticipation() instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            return studentParticipation.getStudent();
        }
        return Optional.empty();
    }

    /**
     * Gets the user's current programming exercise chat session for the exercise, applying a context change first if the
     * session does not already point at this exercise.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return the current chat session, guaranteed to be a valid ask-user session for the exercise
     */
    private IrisChatSession getCurrentAskUserSession(ProgrammingExercise exercise, User user) {
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exercise.getId())) {
            irisChatSessionService.applyContextChange(session, PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        }
        ensureAskUserSession(session, exercise);
        return session;
    }

    /**
     * Finds or creates an empty chat session for the exercise's course, applying a context change first if the session
     * does not already point at this exercise.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return a fresh chat session, guaranteed to be a valid ask-user session for the exercise
     */
    private IrisChatSession getFreshAskUserSession(ProgrammingExercise exercise, User user) {
        var session = irisChatSessionService.findOrCreateEmptySession(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), user);
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exercise.getId())) {
            irisChatSessionService.applyContextChange(session, PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        }
        ensureAskUserSession(session, exercise);
        return session;
    }

    /**
     * Resolves the programming exercise backing an ask-user chat session and validates that ask-user mode is available for it.
     *
     * @param session the chat session
     * @return the programming exercise the session belongs to
     * @throws ConflictException if the session is not a programming exercise chat session, or ask-user mode is unavailable
     */
    private ProgrammingExercise resolveProgrammingExerciseForAskUserSession(IrisChatSession session) {
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT) {
            throw new ConflictException("Iris Ask-user Mode requires a programming exercise chat session", "Iris", "irisAskUserModeInvalidChatMode");
        }

        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getEntityId());
        validateAskUserAvailable(exercise);
        return exercise;
    }

    /**
     * Validates that the user has opted into LLM usage, has at least student role for the exercise, and that ask-user mode
     * is available for the exercise.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @throws ConflictException if ask-user mode is not available for the exercise
     */
    private void validateAskUserAvailable(ProgrammingExercise exercise, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        validateAskUserAvailable(exercise);
    }

    /**
     * Validates that ask-user mode is available for the exercise, i.e. it is not an exam exercise and ask-user mode is
     * enabled in the Iris settings.
     *
     * @param exercise the programming exercise
     * @throws ConflictException if the exercise is an exam exercise or ask-user mode is not enabled
     */
    private void validateAskUserAvailable(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris Ask-user Mode is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
    }

    /**
     * Ensures the given session is a programming exercise chat session bound to the given exercise.
     *
     * @param session  the chat session
     * @param exercise the programming exercise the session is expected to belong to
     * @throws ConflictException if the session is not a programming exercise chat session for this exercise
     */
    private void ensureAskUserSession(IrisChatSession session, ProgrammingExercise exercise) {
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exercise.getId())) {
            throw new ConflictException("Iris Ask-user Mode requires a programming exercise chat session", "Iris", "irisAskUserModeInvalidChatMode");
        }
    }

    /**
     * Handles the {@code QUIZ_FINISHED} pipeline event: deactivates the ask-user pipeline for the session and saves the
     * final verdict and reasoning reported by Pyris.
     *
     * @param session      the ask-user-mode chat session
     * @param user         the student
     * @param exercise     the programming exercise
     * @param statusUpdate the status update containing the final verdict
     * @param inClassQuiz  whether this is the in-class quiz
     */
    private void handleQuizFinished(IrisChatSession session, User user, ProgrammingExercise exercise, PyrisChatStatusUpdateDTO statusUpdate, boolean inClassQuiz) {
        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        session.setInAskUserModePipeline(false);
        irisChatSessionRepository.save(session);

        try {
            if (statusUpdate.verdict() == null) {
                throw new IllegalStateException("Quiz finished without verdict");
            }
            irisAssessmentReviewService.saveAndHandleVerdict(user, exercise, statusUpdate.verdict(), inClassQuiz);
        }
        catch (Exception e) {
            log.error("Error while processing ask-user mode verdict and reasoning {}", statusUpdate.verdict(), e);
        }
    }

    /**
     * Handles the {@code NEXT_QUESTION} pipeline event: appends the reasoning for the answered question to the
     * assessment and increments the count of questions asked in the session.
     *
     * @param session      the ask-user-mode chat session
     * @param user         the student
     * @param exercise     the programming exercise
     * @param statusUpdate the status update containing the verdict/reasoning for the answered question
     * @param inClassQuiz  whether this is the in-class quiz
     */
    private void handleNextQuestion(IrisChatSession session, User user, ProgrammingExercise exercise, PyrisChatStatusUpdateDTO statusUpdate, boolean inClassQuiz) {
        try {
            irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
            if (statusUpdate.verdict() == null) {
                throw new IllegalStateException("Answer has no verdict");
            }
            irisAssessmentReviewService.addReasoning(user, exercise, statusUpdate.verdict().reasoning(), inClassQuiz);

            session.setQuestionsAsked(session.getQuestionsAsked() + 1);
            irisChatSessionRepository.save(session);
        }
        catch (Exception e) {
            log.error("Error while processing ask-user mode reasoning {}", statusUpdate.verdict(), e);
        }
    }

    /**
     * Handles the {@code USER_STARTS_QUIZ} pipeline event by asynchronously requesting the first ask-user question from
     * Pyris.
     *
     * @param session the ask-user-mode chat session
     */
    private void handleUserStartsQuiz(IrisChatSession session) {
        CompletableFuture.runAsync(() -> requestAndHandleResponseAskUser(session, Optional.of(IrisPipeEvent.FIRST_QUESTION.name()), Optional.empty(), Optional.empty()))
                .exceptionally(e -> {
                    log.error("Error while requesting first ask-user question for session {}", session.getId(), e);
                    return null;
                });
    }

    /**
     * Handles the {@code FIRST_QUESTION} pipeline event by incrementing the count of questions asked in the session.
     *
     * @param session      the ask-user-mode chat session
     * @param exercise     the programming exercise
     * @param statusUpdate the status update for the first question
     */
    private void handleFirstQuestion(IrisChatSession session, ProgrammingExercise exercise, PyrisChatStatusUpdateDTO statusUpdate) {
        try {
            irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);

            session.setQuestionsAsked(session.getQuestionsAsked() + 1);
            irisChatSessionRepository.save(session);
        }
        catch (Exception e) {
            log.error("Error while processing first question pipeline callback {}", statusUpdate.verdict(), e);
        }
    }

    /**
     * Resets an in-progress regular (non-in-class) ask-user quiz so that a new build-with-points event can start a fresh
     * quiz. In-class quizzes are left untouched since they must not be interrupted.
     *
     * @param session the chat session
     * @return true if a running regular quiz was reset
     */
    private boolean resetRunningRegularQuizBeforeBuildWithPoints(IrisChatSession session) {
        if (!session.isInAskUserModePipeline() || session.isInClassQuiz()) {
            return false;
        }

        resetAskUserPipeline(session);
        log.info("Reset active regular ask-user quiz state for session {} before handling build with points", session.getId());
        return true;
    }

    /**
     * Resets the ask-user pipeline state for the session with the given ID, if it is currently active, after a Pyris job
     * failed.
     *
     * @param sessionId the ID of the chat session
     * @param jobId     the ID of the failed Pyris job, used only for logging
     * @return true if an active ask-user quiz was reset
     */
    private boolean resetAskUserPipelineAfterPyrisFailure(long sessionId, String jobId) {
        return irisChatSessionRepository.findById(sessionId).map(session -> {
            if (!session.isInAskUserModePipeline()) {
                return false;
            }

            resetAskUserPipeline(session);
            log.warn("Reset ask-user quiz state for session {} after Pyris job {} failed", session.getId(), jobId);
            return true;
        }).orElse(false);
    }

    /**
     * Deactivates the ask-user pipeline for the session, resets its in-class-quiz flag and question count, resets the
     * associated assessment verdict/reasoning, and cancels any running per-question timer.
     *
     * @param session the chat session to reset
     */
    private void resetAskUserPipeline(IrisChatSession session) {
        var inClassQuiz = session.isInClassQuiz();
        session.setInAskUserModePipeline(false);
        session.setInClassQuiz(false);
        session.setQuestionsAsked(0);
        irisChatSessionRepository.save(session);
        resetAssessmentAfterAskUserPipelineReset(session, inClassQuiz);
        stopTimerForSession(session);
    }

    /**
     * Resets the verdict and reasoning of the Iris assessment for the session's user and exercise. Errors are logged and
     * swallowed so that a failure here does not prevent the ask-user pipeline reset from completing.
     *
     * @param session     the chat session whose user and exercise the assessment belongs to
     * @param inClassQuiz whether the in-class or regular assessment should be reset
     */
    private void resetAssessmentAfterAskUserPipelineReset(IrisChatSession session, boolean inClassQuiz) {
        try {
            var user = userRepository.findByIdElseThrow(session.getUserId());
            var exercise = programmingExerciseRepository.findByIdElseThrow(session.getEntityId());
            irisAssessmentReviewService.resetVerdictAndReasoning(user, exercise, inClassQuiz);
        }
        catch (Exception e) {
            log.error("Error while resetting ask-user assessment state for session {}", session.getId(), e);
        }
    }

    /**
     * Parses the given event name into an {@link IrisPipeEvent}.
     *
     * @param event the event name reported by Pyris, or null
     * @return the parsed event, or empty if {@code event} is null or not a known {@link IrisPipeEvent}
     */
    private Optional<IrisPipeEvent> getAskUserEvent(String event) {
        if (event == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(IrisPipeEvent.valueOf(event));
        }
        catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void stopTimerForSession(IrisChatSession session) {
        ScheduledFuture<?> future = quizTimers.remove(session.getId());

        if (future != null) {
            future.cancel(false);
        }
    }
}
