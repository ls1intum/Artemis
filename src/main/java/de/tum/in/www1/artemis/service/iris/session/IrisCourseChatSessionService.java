package de.tum.in.www1.artemis.service.iris.session;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.iris.IrisCourseChatSessionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CourseChatJob;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service to handle the course chat subsystem of Iris.
 */
@Service
@Profile("iris")
public class IrisCourseChatSessionService extends AbstractIrisChatSessionService<IrisCourseChatSession> {

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    private final PyrisPipelineService pyrisPipelineService;

    public IrisCourseChatSessionService(IrisMessageService irisMessageService, IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            IrisCourseChatSessionRepository irisCourseChatSessionRepository, PyrisPipelineService pyrisPipelineService, ObjectMapper objectMapper) {
        super(irisSessionRepository, objectMapper);
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
        this.pyrisPipelineService = pyrisPipelineService;
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
    public void sendOverWebsocket(IrisMessage message) {
        irisChatWebsocketService.sendMessage(message);
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

    private void requestAndHandleResponse(IrisCourseChatSession session, String variant, CompetencyJol competencyJol) {
        var chatSession = (IrisCourseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());

        pyrisPipelineService.executeCourseChatPipeline(variant, chatSession, competencyJol);
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
            irisChatWebsocketService.sendMessage(savedMessage, statusUpdate.stages());
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
        if (!irisSettingsService.isEnabledFor(IrisSubSettingsType.CHAT, course)) {
            return;
        }
        var user = competencyJol.getUser();
        user.hasAcceptedIrisElseThrow();
        var session = getCurrentSessionOrCreateIfNotExistsInternal(course, user, false);
        CompletableFuture.runAsync(() -> requestAndHandleResponse(session, "jol", competencyJol));
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
