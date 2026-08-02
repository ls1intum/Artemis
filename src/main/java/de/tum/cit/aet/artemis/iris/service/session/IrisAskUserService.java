package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
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
import de.tum.cit.aet.artemis.iris.service.websocket.IrisAssessmentQuizWebsocketService;
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

    private final IrisAssessmentQuizWebsocketService irisAssessmentQuizWebsocketService;

    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> quizTimers = new ConcurrentHashMap<>();

    private final Map<Long, ScheduledFuture<?>> inClassQuizTimers = new ConcurrentHashMap<>();

    public IrisAskUserService(IrisSettingsService irisSettingsService, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            IrisChatSessionRepository irisChatSessionRepository, IrisChatSessionService irisChatSessionService, PyrisPipelineService pyrisPipelineService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, UserRepository userRepository, IrisAssessmentReviewService irisAssessmentReviewService,
            IrisAssessmentQuizWebsocketService irisAssessmentQuizWebsocketService, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
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
        this.irisAssessmentQuizWebsocketService = irisAssessmentQuizWebsocketService;
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

        if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) || participation.getExercise().isExamExercise()) {
            return;
        }
        if (!studentParticipation.getStudent().map(User::hasOptedIntoLLMUsage).orElse(false)) {
            return;
        }
        if (!(result.getSubmission() instanceof ProgrammingSubmission latestSubmission)) {
            return;
        }

        var settings = irisSettingsService.getSettingsForExercise(studentParticipation.getProgrammingExercise());
        if (settings.enabled() && settings.askUserModeEnabled() && latestSubmission.getLatestResult() != null && latestSubmission.getLatestResult().getScore() != null
                && latestSubmission.getLatestResult().getScore() > 0 && !irisChatSessionService.shouldSendProgressStalledEvent(studentParticipation)) {
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
        if (pipeEvent.isEmpty() || statusUpdate.result() == null) {
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
        validateInClassQuizIsAvailableOrElseThrow(exercise);
        return startQuizForCurrentSession(exercise, user, true);
    }

    /**
     * Checks whether the student's latest submission has a positive score.
     *
     * @param exercise the programming exercise
     * @param user     the student
     * @return true if the latest submission has points
     */
    public boolean latestSubmissionHasPoints(ProgrammingExercise exercise, User user) {
        return programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin())
                .flatMap(participation -> programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participation.getId()))
                .map(Submission::getLatestResult).map(result -> result.getScore() != null && result.getScore() > 0).orElse(false);
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
        return programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin(), inClass)
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
     * Makes the in-class quiz mode available for students for an exercise.
     *
     * @param exercise the exercise for which the in-class quiz should be made available
     * @return timer information for the active in-class quiz window
     */
    public IrisQuizTimerDTO makeInClassQuizAvailable(ProgrammingExercise exercise) {
        validateAskUserAvailable(exercise);
        irisAssessmentReviewService.deleteInClassAssessmentsForExercise(exercise);

        var settings = irisSettingsService.getSettingsForExercise(exercise).askUserModeSettings();
        var timeLimit = settings.timeLimitInClass() * 60;
        var expiresAt = ZonedDateTime.now().plusMinutes(settings.timeLimitInClass());

        exercise.setIrisInClassQuizTimer(expiresAt);
        programmingExerciseRepository.save(exercise);
        scheduleInClassQuizTimerCleanup(exercise.getId(), expiresAt);
        irisAssessmentQuizWebsocketService.sendInClassQuizStarted(exercise.getId());

        return new IrisQuizTimerDTO(expiresAt, timeLimit);
    }

    /**
     * Returns the currently active in-class quiz timer for an exercise.
     *
     * @param exercise the programming exercise
     * @return timer information or null if no active timer exists
     */
    public IrisQuizTimerDTO getAvailableInClassQuiz(ProgrammingExercise exercise) {
        var expiresAt = exercise.getIrisInClassQuizTimer();
        if (expiresAt == null) {
            return null;
        }

        if (!expiresAt.isAfter(ZonedDateTime.now())) {
            clearInClassQuizTimer(exercise, expiresAt);
            return null;
        }

        var remainingSeconds = Math.max(Duration.between(ZonedDateTime.now(), expiresAt).toSeconds(), 0);
        return new IrisQuizTimerDTO(expiresAt, Math.toIntExact(remainingSeconds));
    }

    /**
     * Returns the question-answer exchanges for a completed ask-user-mode assessment.
     *
     * @param assessment the Iris assessment
     * @param exercise   the exercise
     * @param user       the student
     * @return the ordered question-answer exchanges
     */
    public List<IrisQAExchangeDTO> getQAExchangeDTOList(IrisAssessment assessment, Exercise exercise, User user, boolean inClass) {
        if (!(exercise instanceof ProgrammingExercise)) {
            throw new ConflictException("Ask-user mode is only supported for programming exercises", "Iris", "irisExerciseTypeUnsupported");
        }

        var session = irisChatSessionRepository.findLatestFinishedAskUserModeSessionByExerciseIdAndUserIdAndInClassQuizElseThrow(exercise.getId(), user.getId(), inClass);
        if (assessment == null) {
            throw new ConflictException("Iris Assessment is missing so QAExchangeList cannot be retrieved", "Iris", "irisAssessmentMissing");
        }
        var reasoning = assessment.getReasoning();
        if (reasoning == null || reasoning.isEmpty()) {
            throw new ConflictException("Iris reasoning is missing for assessment", "Iris", "irisReasoningMissing");
        }

        // skip(1) and drop last message because quiz explanation and ask-user_finished messages are not needed
        List<IrisMessage> irisMessages = session.getMessages().stream().filter(message -> message.getSender().equals(IrisMessageSender.LLM) && message.getInAskUserMode()).skip(1)
                .collect(Collectors.collectingAndThen(Collectors.toList(), messages -> messages.isEmpty() ? List.of() : messages.subList(0, messages.size() - 1)));
        List<IrisMessage> userMessages = session.getMessages().stream().filter(message -> message.getSender().equals(IrisMessageSender.USER) && message.getInAskUserMode())
                .toList();

        int maxSize = Math.max(Math.max(irisMessages.size(), userMessages.size()), reasoning.size());

        return IntStream.range(0, maxSize).mapToObj(i -> new IrisQAExchangeDTO(i, i < irisMessages.size() ? irisMessages.get(i).getContent().getFirst().getContentAsString() : "",
                i < userMessages.size() ? userMessages.get(i).getContent().getFirst().getContentAsString() : "", i < reasoning.size() ? reasoning.get(i) : "")).toList();
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
            throw new IllegalStateException("Tab defocus was detected while not in ask-user mode");
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
     * @return timer information for the current question
     */
    public IrisQuizTimerDTO startTimerForCurrentSession(ProgrammingExercise exercise, User user) {
        validateAskUserAvailable(exercise, user);
        var session = getCurrentAskUserSession(exercise, user);
        if (!session.isInAskUserModePipeline()) {
            throw new IllegalStateException("Timer was started while not in ask-user mode");
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

    private void explainAskUserMode(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission latestSubmission, IrisCourseSettings settings) {
        var participationWithAssessment = programmingExerciseStudentParticipationRepository.findWithIrisAssessmentById(studentParticipation.getId()).orElseThrow();
        var exercise = participationWithAssessment.getProgrammingExercise();
        var student = participationWithAssessment.getStudent().orElseThrow();

        var assessment = participationWithAssessment.getIrisAssessment();
        if (assessment == null) {
            irisAssessmentReviewService.createNewAssessment(participationWithAssessment);
        }
        else {
            irisAssessmentReviewService.resetVerdictAndReasoning(assessment);
        }

        var session = getCurrentAskUserSession(exercise, student);

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

    private void requestAndHandleResponseAskUser(IrisChatSession session, Optional<String> event, Optional<IrisCourseSettings> settings,
            Optional<ProgrammingSubmission> latestSubmission) {
        var exercise = resolveProgrammingExerciseForAskUserSession(session);
        var actualSettings = settings.orElseGet(() -> irisSettingsService.getSettingsForExercise(exercise));
        if (!actualSettings.enabled() || !actualSettings.askUserModeEnabled()) {
            throw new ConflictException("Iris Ask-user Mode is not enabled for this exercise", "Iris", "irisDisabled");
        }

        var actualUser = latestSubmission.flatMap(this::getStudentFromSubmission).orElseGet(() -> userRepository.findByIdElseThrow(session.getUserId()));
        var actualLatestSubmission = latestSubmission.flatMap(submission -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(submission.getId()))
                .or(() -> getLatestSubmissionIfExists(exercise, actualUser));

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

    private Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        var participations = exercise.isTeamMode()
                ? programmingExerciseStudentParticipationRepository.findAllWithSubmissionByExerciseIdAndStudentLoginInTeam(exercise.getId(), user.getLogin())
                : programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());

        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(submission -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(submission.getId()));
    }

    private Optional<User> getStudentFromSubmission(ProgrammingSubmission submission) {
        if (submission.getParticipation() instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            return studentParticipation.getStudent();
        }
        return Optional.empty();
    }

    private IrisChatSession getCurrentAskUserSession(ProgrammingExercise exercise, User user) {
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exercise.getId())) {
            irisChatSessionService.applyContextChange(session, PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        }
        ensureAskUserSession(session, exercise);
        return session;
    }

    private IrisChatSession getFreshAskUserSession(ProgrammingExercise exercise, User user) {
        var session = irisChatSessionService.findOrCreateEmptySession(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), user);
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exercise.getId())) {
            irisChatSessionService.applyContextChange(session, PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        }
        ensureAskUserSession(session, exercise);
        return session;
    }

    private ProgrammingExercise resolveProgrammingExerciseForAskUserSession(IrisChatSession session) {
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT) {
            throw new ConflictException("Iris Ask-user Mode requires a programming exercise chat session", "Iris", "irisAskUserModeInvalidChatMode");
        }

        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getEntityId());
        validateAskUserAvailable(exercise);
        return exercise;
    }

    private void validateAskUserAvailable(ProgrammingExercise exercise, User user) {
        user.hasOptedIntoLLMUsageElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        validateAskUserAvailable(exercise);
    }

    private void validateAskUserAvailable(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris Ask-user Mode is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
    }

    private void ensureAskUserSession(IrisChatSession session, ProgrammingExercise exercise) {
        if (session.getMode() != PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exercise.getId())) {
            throw new ConflictException("Iris Ask-user Mode requires a programming exercise chat session", "Iris", "irisAskUserModeInvalidChatMode");
        }
    }

    private void handleQuizFinished(IrisChatSession session, User user, ProgrammingExercise exercise, PyrisChatStatusUpdateDTO statusUpdate, boolean inClassQuiz) {
        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        session.setInAskUserModePipeline(false);
        irisChatSessionRepository.save(session);

        try {
            if (statusUpdate.verdict() == null) {
                throw new Error("Quiz finished without verdict");
            }
            irisAssessmentReviewService.saveAndHandleVerdict(user, exercise, statusUpdate.verdict(), inClassQuiz);
        }
        catch (Exception e) {
            log.error("Error while processing ask-user mode verdict and reasoning {}", statusUpdate.verdict(), e);
        }
    }

    private void handleNextQuestion(IrisChatSession session, User user, ProgrammingExercise exercise, PyrisChatStatusUpdateDTO statusUpdate, boolean inClassQuiz) {
        try {
            irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
            if (statusUpdate.verdict() == null) {
                throw new Error("Answer has no verdict");
            }
            irisAssessmentReviewService.addReasoning(user, exercise, statusUpdate.verdict().reasoning(), inClassQuiz);

            session.setQuestionsAsked(session.getQuestionsAsked() + 1);
            irisChatSessionRepository.save(session);
        }
        catch (Exception e) {
            log.error("Error while processing ask-user mode reasoning {}", statusUpdate.verdict(), e);
        }
    }

    private void handleUserStartsQuiz(IrisChatSession session) {
        CompletableFuture.runAsync(() -> requestAndHandleResponseAskUser(session, Optional.of(IrisPipeEvent.FIRST_QUESTION.name()), Optional.empty(), Optional.empty()))
                .exceptionally(e -> {
                    log.error("Error while requesting first ask-user question for session {}", session.getId(), e);
                    return null;
                });
    }

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

    private boolean resetAskUserPipelineAfterPyrisFailure(long sessionId, String jobId) {
        return irisChatSessionRepository.findById(sessionId).map(session -> {
            if (!session.isInAskUserModePipeline()) {
                return false;
            }

            var inClassQuiz = session.isInClassQuiz();
            session.setInAskUserModePipeline(false);
            session.setInClassQuiz(false);
            session.setQuestionsAsked(0);
            irisChatSessionRepository.save(session);
            resetAssessmentAfterPyrisFailure(session, inClassQuiz);
            stopTimerForSession(session);
            log.warn("Reset ask-user quiz state for session {} after Pyris job {} failed", session.getId(), jobId);
            return true;
        }).orElse(false);
    }

    private void resetAssessmentAfterPyrisFailure(IrisChatSession session, boolean inClassQuiz) {
        try {
            var user = userRepository.findByIdElseThrow(session.getUserId());
            var exercise = programmingExerciseRepository.findByIdElseThrow(session.getEntityId());
            irisAssessmentReviewService.resetVerdictAndReasoning(user, exercise, inClassQuiz);
        }
        catch (Exception e) {
            log.error("Error while resetting ask-user assessment state for session {}", session.getId(), e);
        }
    }

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

    private void validateInClassQuizIsAvailableOrElseThrow(ProgrammingExercise exercise) {
        if (getAvailableInClassQuiz(exercise) == null) {
            throw new ConflictException("The in-class quiz timer has expired or is not active", "Iris", "irisInClassQuizExpired");
        }
    }

    private void scheduleInClassQuizTimerCleanup(long exerciseId, ZonedDateTime expiresAt) {
        var previousTimer = inClassQuizTimers.remove(exerciseId);
        if (previousTimer != null) {
            previousTimer.cancel(false);
        }

        var future = taskScheduler.schedule(() -> programmingExerciseRepository.findById(exerciseId).ifPresent(exercise -> clearInClassQuizTimer(exercise, expiresAt)),
                expiresAt.toInstant());
        inClassQuizTimers.put(exerciseId, future);
    }

    private void clearInClassQuizTimer(ProgrammingExercise exercise, ZonedDateTime expectedExpiresAt) {
        if (Objects.equals(exercise.getIrisInClassQuizTimer(), expectedExpiresAt)) {
            exercise.setIrisInClassQuizTimer(null);
            programmingExerciseRepository.save(exercise);
        }
        inClassQuizTimers.remove(exercise.getId());
    }
}
