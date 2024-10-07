package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

/**
 * Service to handle the course chat subsystem of Iris.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisCourseChatSessionService extends AbstractIrisChatSessionService<IrisCourseChatSession> {

    private static final Logger log = LoggerFactory.getLogger(IrisCourseChatSessionService.class);

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final PyrisPipelineService pyrisPipelineService;

    private final SubmissionRepository submissionRepository;

    private final double SUCCESS_THRESHOLD = 80.0; // TODO: Retrieve configuration from Iris settings

    public IrisCourseChatSessionService(IrisMessageService irisMessageService, IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            IrisCourseChatSessionRepository irisCourseChatSessionRepository, PyrisPipelineService pyrisPipelineService, ObjectMapper objectMapper,
            StudentParticipationRepository studentParticipationRepository, SubmissionRepository submissionRepository) {
        super(irisSessionRepository, objectMapper);
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
        this.pyrisPipelineService = pyrisPipelineService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.submissionRepository = submissionRepository;
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
    public void checkHasAccessTo(User user, IrisCourseChatSession session) {
        user.hasAcceptedIrisElseThrow();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, session.getCourse(), user);
        if (!Objects.equals(session.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Checks if the exercise connected to IrisCourseChatSession has Iris enabled
     *
     * @param session The session to check
     */
    @Override
    public void checkIsFeatureActivatedFor(IrisCourseChatSession session) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, session.getCourse());
    }

    @Override
    public void sendOverWebsocket(IrisCourseChatSession session, IrisMessage message) {
        irisChatWebsocketService.sendMessage(session, message, null);
    }

    @Override
    public void checkRateLimit(User user) {
        rateLimitService.checkRateLimitElseThrow(user);
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisCourseChatSession session) {
        requestAndHandleResponse(session, "default", null);
    }

    private void requestAndHandleResponse(IrisCourseChatSession session, String variant, Object object) {
        var chatSession = (IrisCourseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        pyrisPipelineService.executeCourseChatPipeline(variant, chatSession, object);
    }

    /**
     * Handles the status update of a CourseChatJob by sending the result to the student via the Websocket.
     *
     * @param job          The job that was executed
     * @param statusUpdate The status update of the job
     */
    public void handleStatusUpdate(CourseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var session = (IrisCourseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(job.sessionId());
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

    /**
     * Triggers the course chat in response to a new judgement of learning.
     * If the course chat is not enabled for the course, nothing happens.
     *
     * @param competencyJol The judgement of learning instance to trigger the course chat for
     */
    public void onJudgementOfLearningSet(CompetencyJol competencyJol) {
        var course = competencyJol.getCompetency().getCourse();

        irisSettingsService.isActivatedForElseThrow(IrisEventType.JOL, course);

        var user = competencyJol.getUser();
        user.hasAcceptedIrisElseThrow();
        var session = getCurrentSessionOrCreateIfNotExistsInternal(course, user, false);
        CompletableFuture.runAsync(() -> requestAndHandleResponse(session, "jol", competencyJol));
    }

    /**
     * Triggers the course chat in response to a new submission for a non-exam exercise.
     * Triggers the pipeline only the first time a user successfully submits an exercise.
     * Subsequent successful submissions are ignored.
     *
     * @param result The submission event to trigger the course chat for
     * @throws ConflictException             If the exercise is an exam exercise
     * @throws AccessForbiddenAlertException If the course chat is not enabled for the course
     */
    public void onSubmissionSuccess(Result result) {
        var participation = result.getParticipation();
        if (!(participation instanceof ProgrammingExerciseStudentParticipation studentParticipation)) {
            return;
        }
        var exercise = (ProgrammingExercise) participation.getExercise();

        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();

        irisSettingsService.isActivatedForElseThrow(IrisEventType.PROGRESS_STALLED, course);

        log.info("Submission was successful for user {}", studentParticipation.getParticipant().getName());
        // The submission was successful, so we inform Iris about the successful submission,
        // but before we do that, we check if this is the first successful time out of all submissions out of all submissions for this exercise
        var allSubmissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(studentParticipation.getId());
        var latestSubmission = allSubmissions.getLast();
        var allSuccessful = allSubmissions.stream().filter(submission -> submission.getLatestResult() != null && submission.getLatestResult().getScore() >= SUCCESS_THRESHOLD)
                .count();
        if (allSuccessful == 1 && Objects.requireNonNull(latestSubmission.getLatestResult()).getScore() >= SUCCESS_THRESHOLD) {
            log.info("First successful submission for user {}", studentParticipation.getParticipant().getName());
            var participant = studentParticipation.getParticipant();
            if (participant instanceof User user) {
                setStudentParticipationsToExercise(user.getId(), exercise);
                var session = getCurrentSessionOrCreateIfNotExistsInternal(course, user, false);
                CompletableFuture.runAsync(() -> requestAndHandleResponse(session, "submission_successful", exercise));
            }
            else {
                var team = (Team) participant;
                var teamMembers = team.getStudents();
                for (var user : teamMembers) {
                    setStudentParticipationsToExercise(user.getId(), exercise);
                    var session = getCurrentSessionOrCreateIfNotExistsInternal(course, user, false);
                    CompletableFuture.runAsync(() -> requestAndHandleResponse(session, "submission_successful", exercise));
                }
            }
        }
        else {
            log.info("User {} has already successfully submitted before, so we do not inform Iris about the successful submission",
                    studentParticipation.getParticipant().getName());
        }
    }

    private void setStudentParticipationsToExercise(Long studentId, ProgrammingExercise exercise) {
        // TODO: Write a repository function to pull student participations for a specific exercise instead of a list of exercises
        var studentParticipation = new HashSet<>(
                studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentId, List.of(exercise)));
        exercise.setStudentParticipations(studentParticipation);
    }

    /**
     * Gets the current Iris session for the course and user.
     * If no session exists or if the last session is from a different day, a new one is created.
     *
     * @param course                      The course to get the session for
     * @param user                        The user to get the session for
     * @param sendInitialMessageIfCreated Whether to send an initial message from Iris if a new session is created
     * @return The current Iris session
     */
    public IrisCourseChatSession getCurrentSessionOrCreateIfNotExists(Course course, User user, boolean sendInitialMessageIfCreated) {
        user.hasAcceptedIrisElseThrow();
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, course);
        return getCurrentSessionOrCreateIfNotExistsInternal(course, user, sendInitialMessageIfCreated);
    }

    private IrisCourseChatSession getCurrentSessionOrCreateIfNotExistsInternal(Course course, User user, boolean sendInitialMessageIfCreated) {
        var sessionOptional = irisCourseChatSessionRepository.findLatestByCourseIdAndUserIdWithMessages(course.getId(), user.getId(), Pageable.ofSize(1)).stream().findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();

            // if session is of today we can continue it; otherwise create a new one
            if (session.getCreationDate().withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now(ZoneId.systemDefault()))) {
                checkHasAccessTo(user, session);
                return session;
            }
        }

        // create a new session with an initial message from Iris
        return createSessionInternal(course, user, sendInitialMessageIfCreated);
    }

    /**
     * Creates a new Iris session for the course and user.
     *
     * @param course             The course to create the session for
     * @param user               The user to create the session for
     * @param sendInitialMessage Whether to send an initial message from Iris
     * @return The created Iris session
     */
    public IrisCourseChatSession createSession(Course course, User user, boolean sendInitialMessage) {
        user.hasAcceptedIrisElseThrow();
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, course);
        return createSessionInternal(course, user, sendInitialMessage);
    }

    private IrisCourseChatSession createSessionInternal(Course course, User user, boolean sendInitialMessage) {
        var session = irisCourseChatSessionRepository.save(new IrisCourseChatSession(course, user));

        if (sendInitialMessage) {
            // Run async to allow the session to be returned immediately
            CompletableFuture.runAsync(() -> requestAndHandleResponse(session));
        }

        return session;
    }
}
