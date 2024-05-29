package de.tum.in.www1.artemis.service.iris.session;

import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
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
public class IrisCourseChatSessionService implements IrisChatBasedFeatureInterface<IrisCourseChatSession>, IrisRateLimitedFeatureInterface {

    private final IrisMessageService irisMessageService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisRateLimitService rateLimitService;

    private final PyrisPipelineService pyrisPipelineService;

    public IrisCourseChatSessionService(IrisMessageService irisMessageService, IrisSettingsService irisSettingsService, IrisChatWebsocketService irisChatWebsocketService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository, IrisRateLimitService rateLimitService,
            PyrisPipelineService pyrisPipelineService) {
        this.irisMessageService = irisMessageService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.rateLimitService = rateLimitService;
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
        irisChatWebsocketService.sendMessage(message, null);
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
        var chatSession = (IrisCourseChatSession) irisSessionRepository.findByIdWithMessagesAndContents(session.getId());

        pyrisPipelineService.executeCourseChatPipeline("default", chatSession);
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
            irisChatWebsocketService.sendStatusUpdate(session, statusUpdate.stages());
        }
    }
}
