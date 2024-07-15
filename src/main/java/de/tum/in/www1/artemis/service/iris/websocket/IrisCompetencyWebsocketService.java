package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

/**
 * Websocket for the Iris competency generation feature
 */
@Service
@Profile("iris")
public class IrisCompetencyWebsocketService extends IrisWebsocketService {

    public IrisCompetencyWebsocketService(WebsocketMessagingService websocketMessagingService) {
        super(websocketMessagingService);
    }

    /**
     * Sends a message and/or a status update over the websocket to the user
     * involved in the session. At least one of the message or the stages must be
     * non-null, otherwise there is no need to send a message.
     *
     * @param stages that should be sent over the websocket (nullable)
     */
    public void sendCompetencies(User user, long courseId, List<PyrisStageDTO> stages) {
        var topic = "competencies/" + courseId;
        super.send(user, topic, stages);
    }

}
