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

    private final IrisRateLimitService rateLimitService;

    public IrisChatWebsocketService(WebsocketMessagingService websocketMessagingService, IrisRateLimitService rateLimitService) {
        super(websocketMessagingService);
        this.rateLimitService = rateLimitService;
    }

    private User checkSessionTypeAndGetUser(IrisSession irisSession) {
        if (!(irisSession instanceof IrisChatSession chatSession)) {
            throw new UnsupportedOperationException("Only IrisChatSessions are supported");
        }
        return chatSession.getUser();
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param irisMessage that should be sent over the websocket
     * @param stages      that should be sent over the websocket
     * @param suggestions that should be sent over the websocket
     */
    public void sendMessage(IrisMessage irisMessage, List<PyrisStageDTO> stages, List<String> suggestions) {
        var session = irisMessage.getSession();
        var user = checkSessionTypeAndGetUser(session);
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        super.send(user, session.getId(), new IrisWebsocketDTO(irisMessage, rateLimitInfo, stages, suggestions));
    }

    public void sendStatusUpdate(IrisSession session, List<PyrisStageDTO> stages) {
        var user = checkSessionTypeAndGetUser(session);
        super.send(user, session.getId(), new IrisWebsocketDTO(null, rateLimitService.getRateLimitInformation(user), stages, null));
    }
}
