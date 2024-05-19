package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

@Service
@Profile("iris")
public class IrisChatWebsocketService extends IrisWebsocketService {

    private static final String WEBSOCKET_TOPIC_SESSION_TYPE = "sessions";

    private final IrisRateLimitService rateLimitService;

    public IrisChatWebsocketService(WebsocketMessagingService websocketMessagingService, IrisRateLimitService rateLimitService) {
        super(websocketMessagingService);
        this.rateLimitService = rateLimitService;
    }

    private User checkSessionTypeAndGetUser(IrisSession irisSession) {
        if (!(irisSession instanceof IrisChatSession chatSession)) {
            throw new UnsupportedOperationException("Only IrisChatSession is supported");
        }
        return chatSession.getUser();
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param irisMessage that should be sent over the websocket
     * @param stages      that should be sent over the websocket
     */
    public void sendMessage(IrisMessage irisMessage, List<PyrisStageDTO> stages) {
        var session = irisMessage.getSession();
        var user = checkSessionTypeAndGetUser(session);
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), new IrisWebsocketDTO(irisMessage, null, rateLimitInfo, stages));
    }

    /**
     * Sends an exception over the websocket to a specific user
     *
     * @param session   to which the exception belongs
     * @param throwable that should be sent over the websocket
     * @param stages    that should be sent over the websocket
     */
    public void sendException(IrisSession session, Throwable throwable, List<PyrisStageDTO> stages) {
        User user = checkSessionTypeAndGetUser(session);
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), new IrisWebsocketDTO(null, throwable, rateLimitInfo, stages));
    }

    public void sendStatusUpdate(IrisSession session, List<PyrisStageDTO> stages) {
        var user = checkSessionTypeAndGetUser(session);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), new IrisWebsocketDTO(null, null, rateLimitService.getRateLimitInformation(user), stages));
    }
}
