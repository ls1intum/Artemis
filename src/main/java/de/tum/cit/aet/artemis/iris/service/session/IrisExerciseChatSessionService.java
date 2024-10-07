package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
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
@Service
@Profile(PROFILE_IRIS)
public class IrisExerciseChatSessionService extends AbstractIrisChatSessionService<IrisExerciseChatSession> implements IrisRateLimitedFeatureInterface {

    private static final Logger log = LoggerFactory.getLogger(IrisExerciseChatSessionService.class);

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    private final SubmissionRepository submissionRepository;

    private final double SUCCESS_THRESHOLD = 100.0; // TODO: Retrieve configuration from Iris settings

    public IrisExerciseChatSessionService(IrisMessageService irisMessageService, IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            IrisRateLimitService rateLimitService, PyrisPipelineService pyrisPipelineService, ProgrammingExerciseRepository programmingExerciseRepository,
            ObjectMapper objectMapper, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository, SubmissionRepository submissionRepository) {
        super(irisSessionRepository, objectMapper);
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.rateLimitService = rateLimitService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    // TODO: This function is only used in tests. Replace with createSession once the tests are refactored.
    public IrisExerciseChatSession createChatSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return irisSessionRepository.save(new IrisExerciseChatSession(exercise, user));
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param user    The user to check
     * @param session The session to check
     */
    @Override
    public void checkHasAccessTo(User user, IrisExerciseChatSession session) {
        checkHasTheMinimalRequiredRoleForExerciseElseThrow(user, session.getExercise());
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    private void checkHasTheMinimalRequiredRoleForExerciseElseThrow(User user, Exercise exercise) {
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
    }

    /**
     * Checks if the exercise connected to IrisChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisExerciseChatSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, session.getExercise());
    }

    @Override
    public void sendOverWebsocket(IrisExerciseChatSession session, IrisMessage message) {
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
    public void requestAndHandleResponse(IrisExerciseChatSession session) {
        requestAndHandleResponse(session, "default");
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     * @param variant The variant of the pipeline to use
     */
    public void requestAndHandleResponse(IrisExerciseChatSession session, String variant) {
        var chatSession = (IrisExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        if (chatSession.getExercise().isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(chatSession.getExercise().getId());
        var latestSubmission = getLatestSubmissionIfExists(exercise, chatSession.getUser());
        pyrisPipelineService.executeExerciseChatPipeline(variant, latestSubmission, exercise, chatSession);
    }

    /**
     * Handles the build failure event by sending a message to the student via Iris.
     *
     * @param result The result of the submission
     */
    public void onBuildFailure(Result result) {
        var submission = result.getSubmission();
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            var participation = programmingSubmission.getParticipation();
            if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation)) {
                return;
            }
            var exercise = (ProgrammingExercise) participation.getExercise();
            if (exercise.isExamExercise()) {
                throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
            }

            irisSettingsService.isActivatedForElseThrow(IrisEventType.BUILD_FAILED, exercise);

            var participant = studentParticipation.getParticipant();
            if (participant instanceof User user) {
                var session = getCurrentSessionOrCreateIfNotExistsInternal(exercise, user, false);
                log.info("Build failed for user {}", user.getName());
                CompletableFuture.runAsync(() -> requestAndHandleResponse(session, "build_failed"));
            }
            else {
                throw new ConflictException("Build failure event is not supported for team participations", "Iris", "irisTeamParticipation");
            }
        }
    }

    /**
     * Informs Iris about a progress stall event, if the student has not improved their in the last 3 submissions.
     *
     * @param result The result of the submission
     */
    public void onNewResult(Result result) {
        var participation = result.getParticipation();
        if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation)) {
            return;
        }
        var exercise = (ProgrammingExercise) participation.getExercise();
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }

        irisSettingsService.isActivatedForElseThrow(IrisEventType.PROGRESS_STALLED, exercise);

        var recentSubmissions = submissionRepository.findAllWithResultsAndAssessorByParticipationIdOrderBySubmissionDateAsc(studentParticipation.getId());

        // Check if the user has already successfully submitted before
        var successfulSubmission = recentSubmissions.stream()
                .anyMatch(submission -> submission.getLatestResult() != null && submission.getLatestResult().getScore() == SUCCESS_THRESHOLD);
        if (!successfulSubmission && recentSubmissions.size() >= 3) {
            var listOfScores = recentSubmissions.stream().map(Submission::getLatestResult).map(Result::getScore).toList();

            // Check if the student needs intervention based on their recent score trajectory
            var needsIntervention = needsIntervention(listOfScores, 3);
            if (needsIntervention) {
                log.info("Scores in the last 3 submissions did not improve for user {}", studentParticipation.getParticipant().getName());
                var participant = ((ProgrammingExerciseStudentParticipation) participation).getParticipant();
                if (participant instanceof User user) {
                    var session = getCurrentSessionOrCreateIfNotExistsInternal(exercise, user, false);
                    CompletableFuture.runAsync(() -> requestAndHandleResponse(session, "progress_stalled"));
                }
                else {
                    throw new ConflictException("Progress stalled event is not supported for team participations", "Iris", "irisTeamParticipation");
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

    private Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        var participations = programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(sub.getId()));
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
     * @param scores       The list of all scores for the student.
     * @param intervalSize The number of recent submissions to consider.
     * @return true if intervention is needed, false otherwise.
     */
    private boolean needsIntervention(List<Double> scores, int intervalSize) {
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
    public IrisExerciseChatSession getCurrentSessionOrCreateIfNotExists(ProgrammingExercise exercise, User user, boolean sendInitialMessageIfCreated) {
        user.hasAcceptedIrisElseThrow();
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        return getCurrentSessionOrCreateIfNotExistsInternal(exercise, user, sendInitialMessageIfCreated);
    }

    private IrisExerciseChatSession getCurrentSessionOrCreateIfNotExistsInternal(ProgrammingExercise exercise, User user, boolean sendInitialMessageIfCreated) {
        var sessionOptional = irisExerciseChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(exercise.getId(), user.getId(), Pageable.ofSize(1)).stream()
                .findFirst();

        return sessionOptional.orElseGet(() -> createSessionInternal(exercise, user, sendInitialMessageIfCreated));
    }

    public IrisExerciseChatSession createSession(ProgrammingExercise exercise, User user, boolean sendInitialMessage) {
        user.hasAcceptedIrisElseThrow();
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, exercise);
        checkHasTheMinimalRequiredRoleForExerciseElseThrow(user, exercise);
        return createSessionInternal(exercise, user, sendInitialMessage);
    }

    private IrisExerciseChatSession createSessionInternal(ProgrammingExercise exercise, User user, boolean sendInitialMessage) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }

        var session = irisExerciseChatSessionRepository.save(new IrisExerciseChatSession(exercise, user));

        if (sendInitialMessage) {
            // Run async to allow the session to be returned immediately
            CompletableFuture.runAsync(() -> requestAndHandleResponse(session));
        }

        return session;
    }

    /**
     * Handles the status update of a ExerciseChatJob by sending the result to the student via the Websocket.
     *
     * @param job          The job that was executed
     * @param statusUpdate The status update of the job
     */
    public void handleStatusUpdate(ExerciseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var session = (IrisExerciseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
        if (statusUpdate.result() != null) {
            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(statusUpdate.result()));
            var savedMessage = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            irisChatWebsocketService.sendMessage(session, savedMessage, statusUpdate.stages());
        }
        else {
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages(), statusUpdate.suggestions());
        }

        updateLatestSuggestions(session, statusUpdate.suggestions());
    }
}
