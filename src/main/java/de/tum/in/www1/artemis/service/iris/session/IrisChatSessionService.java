package de.tum.in.www1.artemis.service.iris.session;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisModelService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service to handle the chat subsystem of Iris.
 */
@Service
public class IrisChatSessionService implements IrisSessionSubServiceInterface {

    private final Logger log = LoggerFactory.getLogger(IrisChatSessionService.class);

    private final IrisModelService irisModelService;

    private final IrisMessageService irisMessageService;

    private final IrisWebsocketService irisWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    public IrisChatSessionService(IrisModelService irisModelService, IrisMessageService irisMessageService, IrisWebsocketService irisWebsocketService,
            AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository) {
        this.irisModelService = irisModelService;
        this.irisMessageService = irisMessageService;
        this.irisWebsocketService = irisWebsocketService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * Checks if the user has access to the Iris session.
     * A user has access if they have access to the exercise and the session belongs to them.
     * If the user is null, the user is fetched from the database.
     *
     * @param session The session to check
     * @param user    The user to check
     */
    @Override
    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        var chatSession = castToSessionType(session, IrisChatSession.class);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, chatSession.getExercise(), user);
        if (!Objects.equals(chatSession.getUser(), user)) {
            throw new AccessForbiddenException("Iris Session", session.getId());
        }
    }

    /**
     * Sends all messages of the session to an LLM and handles the response by saving the message
     * and sending it to the student via the Websocket.
     *
     * @param session The chat session to send to the LLM
     */
    @Override
    public void requestAndHandleResponse(IrisSession session) {
        var fullSession = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
        irisModelService.requestResponse(fullSession).handleAsync((irisMessage, throwable) -> {
            if (throwable != null) {
                log.error("Error while getting response from Iris model", throwable);
            }
            else if (irisMessage != null) {
                var irisMessageSaved = irisMessageService.saveMessage(irisMessage, fullSession, IrisMessageSender.LLM);
                irisWebsocketService.sendMessage(irisMessageSaved);
            }
            else {
                log.error("No response from Iris model");
            }
            return null;
        });
    }
}
