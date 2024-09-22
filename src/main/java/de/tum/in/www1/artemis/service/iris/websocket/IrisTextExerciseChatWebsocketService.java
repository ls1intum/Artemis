package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisTextExerciseChatSession;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.dto.IrisChatWebsocketDTO;

@Service
@Profile("iris")
public class IrisTextExerciseChatWebsocketService {

    private final IrisWebsocketService websocketService;

    private final IrisRateLimitService rateLimitService;

    public IrisTextExerciseChatWebsocketService(IrisWebsocketService websocketService, IrisRateLimitService rateLimitService) {
        this.websocketService = websocketService;
        this.rateLimitService = rateLimitService;
    }

    public void sendMessage(IrisTextExerciseChatSession session, IrisMessage message, List<PyrisStageDTO> stages) {
        var rateLimitInfo = rateLimitService.getRateLimitInformation(session.getUser());
        var topic = String.valueOf(session.getId()); // Todo: add more specific topic
        var payload = new IrisChatWebsocketDTO(message, rateLimitInfo, stages, null);
        websocketService.send(session.getUser().getLogin(), topic, payload);
    }

}
