package de.tum.cit.aet.artemis.iris.service.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@Service
@Profile(PROFILE_IRIS)
public class IrisChatWebsocketService {

    private final IrisWebsocketService websocketService;

    private final IrisRateLimitService rateLimitService;

    public IrisChatWebsocketService(IrisWebsocketService websocketService, IrisRateLimitService rateLimitService) {
        this.websocketService = websocketService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Sends a message and/or a status update over the websocket to the user
     * involved in the session. At least one of the message or the stages must be
     * non-null, otherwise there is no need to send a message.
     * This is currently used for both the exercise and course chat sessions, but
     * this could be split up in the future.
     *
     * @param session     the session to send the message to
     * @param irisMessage that should be sent over the websocket
     * @param stages      that should be sent over the websocket
     */
    public void sendMessage(IrisChatSession session, IrisMessage irisMessage, List<PyrisStageDTO> stages) {
        var user = session.getUser();
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        var topic = "" + session.getId(); // Todo: add more specific topic
        var payload = new IrisChatWebsocketDTO(irisMessage, rateLimitInfo, stages, null, null);
        websocketService.send(user.getLogin(), topic, payload);
    }

    /**
     * Sends a status update over the websocket to a specific user
     *
     * @param session the session to send the status update to
     * @param stages  the stages to send
     */
    public void sendStatusUpdate(IrisChatSession session, List<PyrisStageDTO> stages) {
        this.sendStatusUpdate(session, stages, null, null);
    }

    /**
     * Sends a status update over the websocket to a specific user
     *
     * @param session     the session to send the status update to
     * @param stages      the stages to send
     * @param suggestions the suggestions to send
     * @param tokens      token usage and cost send by Pyris
     */
    public void sendStatusUpdate(IrisChatSession session, List<PyrisStageDTO> stages, List<String> suggestions, List<LLMRequest> tokens) {
        var user = session.getUser();
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        var topic = "" + session.getId(); // Todo: add more specific topic
        var payload = new IrisChatWebsocketDTO(null, rateLimitInfo, stages, suggestions, tokens);
        websocketService.send(user.getLogin(), topic, payload);
    }
}
