package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedProgrammingExerciseChatSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisExerciseChatSessionService extends AbstractIrisChatSessionService<IrisProgrammingExerciseChatSession> {

    private static final Logger log = LoggerFactory.getLogger(IrisExerciseChatSessionService.class);

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    private final SubmissionRepository submissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final MessageSource messageSource;

    public IrisExerciseChatSessionService(IrisMessageService irisMessageService, IrisMessageRepository irisMessageRepository, LLMTokenUsageService llmTokenUsageService,
            IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService,
            ProgrammingExerciseRepository programmingExerciseRepository, ObjectMapper objectMapper, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository,
            SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository, UserRepository userRepository, MessageSource messageSource) {
        super(irisSessionRepository, programmingSubmissionRepository, programmingExerciseStudentParticipationRepository, objectMapper, irisMessageService, irisMessageRepository,
                irisChatWebsocketService, llmTokenUsageService);
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.messageSource = messageSource;
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisProgrammingExerciseChatSession session) {
        var exercise = exerciseRepository.findByIdElseThrow(session.getExerciseId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (!Objects.equals(session.getUserId(), user.getId())) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisProgrammingExerciseChatSession session) {
        var exercise = exerciseRepository.findByIdElseThrow(session.getExerciseId());
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, exercise);
    }

    @Override
    public void sendOverWebsocket(IrisProgrammingExerciseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket. Uses the default pipeline variant.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisProgrammingExerciseChatSession session) {
        requestAndHandleResponse(session, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    /**
     * Sends all messages of the session to an LLM with uncommitted file changes and handles the response.
     *
     * @param session          The chat session to send to the LLM
     * @param uncommittedFiles The uncommitted files from the client (working copy)
     */
    public void requestAndHandleResponseWithUncommittedChanges(IrisProgrammingExerciseChatSession session, Map<String, String> uncommittedFiles) {
        requestAndHandleResponse(session, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), uncommittedFiles);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session          The chat session to send to the LLM
     * @param event            The event to trigger on Pyris side
     * @param settings         Optional settings to use if already loaded elsewhere. If not provided, the settings will be fetched
     * @param user             Optional user to use if already loaded elsewhere. If not provided, the user will be fetched
     * @param latestSubmission Optional latest submission to use if already loaded elsewhere. If not provided, the latest submission will be fetched
     */
    public void requestAndHandleResponse(IrisProgrammingExerciseChatSession session, Optional<String> event, Optional<IrisCombinedProgrammingExerciseChatSubSettingsDTO> settings,
            Optional<User> user, Optional<ProgrammingSubmission> latestSubmission) {
        requestAndHandleResponse(session, event, settings, user, latestSubmission, Map.of());
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session          The chat session to send to the LLM
     * @param event            The event to trigger on Pyris side
     * @param settings         Optional settings to use if already loaded elsewhere. If not provided, the settings will be fetched
     * @param user             Optional user to use if already loaded elsewhere. If not provided, the user will be fetched
     * @param latestSubmission Optional latest submission to use if already loaded elsewhere. If not provided, the latest submission will be fetched
     * @param uncommittedFiles The uncommitted files from the client (working copy)
     */
    private void requestAndHandleResponse(IrisProgrammingExerciseChatSession session, Optional<String> event, Optional<IrisCombinedProgrammingExerciseChatSubSettingsDTO> settings,
            Optional<User> user, Optional<ProgrammingSubmission> latestSubmission, Map<String, String> uncommittedFiles) {
        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getExerciseId());
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }

        var actualSettings = settings.orElseGet(() -> irisSettingsService.getCombinedIrisSettingsFor(exercise, false).irisProgrammingExerciseChatSettings());
        if (!actualSettings.enabled()) {
            throw new ConflictException("Iris is not enabled for this exercise", "Iris", "irisDisabled");
        }

        var actualUser = latestSubmission.flatMap(s -> ((ProgrammingExerciseStudentParticipation) s.getParticipation()).getStudent())
                .orElseGet(() -> userRepository.findByIdElseThrow(session.getUserId()));
        var actualLatestSubmission = latestSubmission.or(() -> getLatestSubmissionIfExists(exercise, actualUser));

        var chatSession = (IrisProgrammingExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        pyrisPipelineService.executeExerciseChatPipeline(actualSettings.selectedVariant(), actualSettings.customInstructions(), actualLatestSubmission, exercise, chatSession,
                event, uncommittedFiles);
    }

    /**
     * Handles the new result event by checking if the user has accepted external LLM usage and
     * if the participation is a student participation. If so, it checks if the build failed or if
     * the student needs intervention based on their recent score trajectory.
     *
     * @param resultEvent The result event of the submission
     */
    public void handleNewResultEvent(NewResultEvent resultEvent) {
        var result = resultEvent.getEventObject();
        var participation = result.getSubmission().getParticipation();

        // Only support programming exercises that are not exam exercises
        if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) || participation.getExercise().isExamExercise()) {
            return;
        }

        // If the user has not accepted external LLM usage, or participation is of a team, we do not proceed
        if (!studentParticipation.getStudent().map(User::hasAcceptedExternalLLMUsage).orElse(true)) {
            return;
        }

        var programmingSubmission = (ProgrammingSubmission) result.getSubmission();
        if (programmingSubmission.isBuildFailed()) {
            onBuildFailure(studentParticipation, programmingSubmission);
        }
        else {
            onNewResult(studentParticipation, programmingSubmission);
        }
    }

    /**
     * Handles the build failure event by sending a message to the student via Iris.
     */
    private void onBuildFailure(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission submission) {
        var combinedSettings = irisSettingsService.getCombinedIrisSettingsFor(studentParticipation.getProgrammingExercise(), false);
        var settings = combinedSettings.irisProgrammingExerciseChatSettings();
        if (!settings.enabled() || !IrisSettingsService.isEventEnabledInSettings(combinedSettings, IrisEventType.BUILD_FAILED)) {
            return;
        }

        var user = studentParticipation.getStudent().orElseThrow();
        var session = getCurrentSessionOrCreateIfNotExistsInternal(studentParticipation.getProgrammingExercise(), user, false);
        log.info("Build failed for user {}", user.getName());
        CompletableFuture.runAsync(() -> requestAndHandleResponse(session, Optional.of(IrisEventType.BUILD_FAILED.name().toLowerCase()), Optional.of(settings), Optional.of(user),
                Optional.of(submission)));
    }

    /**
     * Informs Iris about a progress stall event, if the student has not improved their in the last 3 submissions.
     */
    private void onNewResult(ProgrammingExerciseStudentParticipation studentParticipation, ProgrammingSubmission latestSubmission) {
        var combinedSettings = irisSettingsService.getCombinedIrisSettingsFor(studentParticipation.getProgrammingExercise(), false);
        var settings = combinedSettings.irisProgrammingExerciseChatSettings();
        if (!settings.enabled() || !IrisSettingsService.isEventEnabledInSettings(combinedSettings, IrisEventType.PROGRESS_STALLED)) {
            return;
        }

        // TODO: Reduce this call to the last 5 submissions or sth
        var recentSubmissions = submissionRepository.findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(studentParticipation.getId());

        double successThreshold = 100.0; // TODO: Retrieve configuration from Iris settings

        // Check if the user has already successfully submitted before
        var successfulSubmission = recentSubmissions.stream()
                .anyMatch(submission -> submission.getLatestResult() != null && submission.getLatestResult().getScore() == successThreshold);
        if (!successfulSubmission && recentSubmissions.size() >= 3) {
            var listOfScores = recentSubmissions.stream().map(Submission::getLatestResult).filter(Objects::nonNull).map(Result::getScore).toList();

            // Check if the student needs intervention based on their recent score trajectory
            var needsIntervention = needsIntervention(listOfScores);
            if (needsIntervention) {
                log.info("Scores in the last 3 submissions did not improve for user {}", studentParticipation.getParticipant().getName());
                var user = studentParticipation.getStudent().orElseThrow();
                var session = getCurrentSessionOrCreateIfNotExistsInternal(studentParticipation.getProgrammingExercise(), user, false);
                try {
                    CompletableFuture.runAsync(() -> requestAndHandleResponse(session, Optional.of(IrisEventType.PROGRESS_STALLED.name().toLowerCase()), Optional.of(settings),
                            Optional.of(user), Optional.of(latestSubmission)));
                }
                catch (Exception e) {
                    log.error("Error while sending progress stalled message to Iris for user {}", studentParticipation.getParticipant().getName(), e);
                }
            }
        }
        else {
            log.info("Submission was not successful for user {}", studentParticipation.getParticipant().getName());
            if (successfulSubmission) {
                log.info("User {} has already successfully submitted before, so we do not inform Iris about the submission failure",
                        studentParticipation.getParticipant().getName());
            }
        }
    }

    /**
     * Checks if there's overall improvement in the given interval [i, j] of the list.
     *
     * @param scores The list of scores.
     * @param i      The starting index of the interval (inclusive).
     * @param j      The ending index of the interval (inclusive).
     * @return true if there's overall improvement (last score > first score), false otherwise.
     */
    private boolean hasOverallImprovement(List<Double> scores, int i, int j) {
        if (i >= j || i < 0 || j >= scores.size()) {
            throw new IllegalArgumentException("Invalid interval");
        }

        return scores.get(j) > scores.get(i) && IntStream.range(i, j).allMatch(index -> scores.get(index) <= scores.get(index + 1));
    }

    /**
     * Checks if the student needs intervention based on their recent score trajectory.
     *
     * @param scores The list of all scores for the student.
     * @return true if intervention is needed, false otherwise.
     */
    private boolean needsIntervention(List<Double> scores) {
        int intervalSize = 3; // TODO: Retrieve configuration from Iris settings
        if (scores.size() < intervalSize) {
            return false; // Not enough data to make a decision
        }

        int lastIndex = scores.size() - 1;
        int startIndex = lastIndex - intervalSize + 1;

        return !hasOverallImprovement(scores, startIndex, lastIndex);
    }

    /**
     * Gets the current Iris session for the exercise and user.
     * If no session exists or if the last session is from a different day, a new one is created.
     *
     * @param exercise                    Programming exercise to get the session for
     * @param user                        The user to get the session for
     * @param sendInitialMessageIfCreated Whether to send an initial message from Iris if a new session is created
     * @return The current Iris session
     */
    public IrisProgrammingExerciseChatSession getCurrentSessionOrCreateIfNotExists(ProgrammingExercise exercise, User user, boolean sendInitialMessageIfCreated) {
        user.hasAcceptedExternalLLMUsageElseThrow();
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, exercise);
        return getCurrentSessionOrCreateIfNotExistsInternal(exercise, user, sendInitialMessageIfCreated);
    }

    private IrisProgrammingExerciseChatSession getCurrentSessionOrCreateIfNotExistsInternal(ProgrammingExercise exercise, User user, boolean sendInitialMessageIfCreated) {
        var sessionOptional = irisExerciseChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId(), Pageable.ofSize(1)).stream()
                .findFirst();

        return sessionOptional.orElseGet(() -> createSessionInternal(exercise, user, sendInitialMessageIfCreated));
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     *
     * @param exercise           The exercise the session belongs to
     * @param user               The user the session belongs to
     * @param sendInitialMessage Whether to send an initial message from Iris
     * @return The created session
     */
    public IrisProgrammingExerciseChatSession createSession(ProgrammingExercise exercise, User user, boolean sendInitialMessage) {
        user.hasAcceptedExternalLLMUsageElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, exercise);
        return createSessionInternal(exercise, user, sendInitialMessage);
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     *
     * @param exercise           The exercise the session belongs to
     * @param user               The user the session belongs to
     * @param sendInitialMessage Whether to send an initial message from Iris
     * @return The created session
     */
    private IrisProgrammingExerciseChatSession createSessionInternal(ProgrammingExercise exercise, User user, boolean sendInitialMessage) {
        checkIfExamExercise(exercise);

        var exerciseChat = new IrisProgrammingExerciseChatSession(exercise, user);
        exerciseChat.setTitle(AbstractIrisChatSessionService.getLocalizedNewChatTitle(user.getLangKey(), messageSource));
        var session = irisExerciseChatSessionRepository.save(exerciseChat);

        if (sendInitialMessage) {
            // Run async to allow the session to be returned immediately
            CompletableFuture.runAsync(() -> requestAndHandleResponse(session));
        }

        return session;
    }

    @Override
    protected void setLLMTokenUsageParameters(LLMTokenUsageService.LLMTokenUsageBuilder builder, IrisProgrammingExerciseChatSession session) {
        var exercise = exerciseRepository.findByIdElseThrow(session.getExerciseId());
        builder.withCourse(exercise.getCourseViaExerciseGroupOrCourseMember().getId()).withExercise(exercise.getId());
    }

    /**
     * Checks if the exercise is an exam exercise and throws an exception if it is.
     *
     * @param exercise The exercise to check
     */
    private void checkIfExamExercise(Exercise exercise) {
        if (exercise.isExamExercise()) {
            throw new IrisUnsupportedExerciseTypeException("Iris is not supported for exam exercises");
        }
    }
}
