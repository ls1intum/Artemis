package de.tum.in.www1.artemis.service.iris.websocket;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;

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
     * @param userLogin    the login of the user to send the message to
     * @param courseId     the id of the course the message is related to
     * @param statusUpdate the status update to send with the stages and competencies generated so far (if any)
     */
    public void sendCompetencies(String userLogin, long courseId, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        var topic = "competencies/" + courseId;
        super.send(userLogin, topic, statusUpdate);
    }

}
