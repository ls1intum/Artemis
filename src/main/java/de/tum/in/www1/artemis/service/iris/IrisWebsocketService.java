package de.tum.in.www1.artemis.service.iris;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;

/**
 * A service to send a message over the websocket to a specific user
 */
@Service
public class IrisWebsocketService {

    private static final String IRIS_WEBSOCKET_TOPIC_PREFIX = "/topic/iris";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param irisMessage that should be send over the websocket
     */
    public void sendMessage(IrisMessage irisMessage) {
        Long irisSessionId = irisMessage.getSession().getId();
        String userLogin = irisMessage.getSession().getUser().getLogin();
        String irisWebsocketTopic = String.format("%s/sessions/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, irisSessionId);
        websocketMessagingService.sendMessageToUser(userLogin, irisWebsocketTopic, new IrisWebsocketDTO(irisMessage));
    }

    /**
     * Sends an exception over the websocket to a specific user
     *
     * @param irisSession to which the exception belongs
     * @param throwable   that should be send over the websocket
     */
    public void sendException(IrisSession irisSession, Throwable throwable) {
        Long irisSessionId = irisSession.getId();
        String userLogin = irisSession.getUser().getLogin();
        String irisWebsocketTopic = String.format("%s/sessions/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, irisSessionId);
        websocketMessagingService.sendMessageToUser(userLogin, irisWebsocketTopic, new IrisWebsocketDTO(throwable));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class IrisWebsocketDTO {

        private final IrisWebsocketMessageType type;

        private final IrisMessage message;

        private final String errorMessage;

        private final String errorTranslationKey;

        public IrisWebsocketDTO(IrisMessage message) {
            this.type = IrisWebsocketMessageType.IRIS_MESSAGE;
            this.message = message;
            this.errorMessage = null;
            this.errorTranslationKey = null;
        }

        public IrisWebsocketDTO(Throwable throwable) {
            this.type = IrisWebsocketMessageType.ERROR;
            this.message = null;
            this.errorMessage = throwable.getMessage();
            this.errorTranslationKey = throwable instanceof IrisException irisException ? irisException.getTranslationKey() : null;
        }

        public IrisWebsocketMessageType getType() {
            return type;
        }

        public IrisMessage getMessage() {
            return message;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorTranslationKey() {
            return errorTranslationKey;
        }

        public enum IrisWebsocketMessageType {
            IRIS_MESSAGE, ERROR;
        }
    }
}
