package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;

/**
 * A service to send a message over the websocket to a specific user
 */
public abstract class IrisWebsocketService {

    private static final String IRIS_WEBSOCKET_TOPIC_PREFIX = "/topic/iris";

    private final WebsocketMessagingService websocketMessagingService;
    
    private final String topic;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService, String topic) {
        this.websocketMessagingService = websocketMessagingService;
        this.topic = topic;
    }
    
    /**
     * Checks if the session is of the correct type
     * @param irisSession to check
     */
    protected abstract void checkSessionType(IrisSession irisSession);
    
    /**
     * Gets the user login of the user to which the message should be sent
     * @param irisSession with the user to which the message should be sent
     * @return the user login of the user to which the message should be sent
     */
    protected abstract String getUserLogin(IrisSession irisSession);
    
    /**
     * Sends a message over the websocket to a specific user
     *
     * @param irisMessage that should be sent over the websocket
     */
    public void sendMessage(IrisMessage irisMessage) {
        var session = irisMessage.getSession();
        checkSessionType(session);
        String userLogin = getUserLogin(session);
        String irisWebsocketTopic = String.format("%s/%s/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, topic, session.getId());
        websocketMessagingService.sendMessageToUser(userLogin, irisWebsocketTopic, new IrisWebsocketDTO(irisMessage));
    }

    /**
     * Sends an exception over the websocket to a specific user
     *
     * @param irisSession to which the exception belongs
     * @param throwable   that should be sent over the websocket
     */
    public void sendException(IrisSession irisSession, Throwable throwable) {
        checkSessionType(irisSession);
        String userLogin = getUserLogin(irisSession);
        String irisWebsocketTopic = String.format("%s/%s/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, topic, irisSession.getId());
        websocketMessagingService.sendMessageToUser(userLogin, irisWebsocketTopic, new IrisWebsocketDTO(throwable));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class IrisWebsocketDTO {

        private final IrisWebsocketMessageType type;

        private final IrisMessage message;

        private final String errorMessage;

        private final String errorTranslationKey;

        private final Map<String, Object> translationParams;

        public IrisWebsocketDTO(IrisMessage message) {
            this.type = IrisWebsocketMessageType.MESSAGE;
            this.message = message;
            this.errorMessage = null;
            this.errorTranslationKey = null;
            this.translationParams = null;
        }

        public IrisWebsocketDTO(Throwable throwable) {
            this.type = IrisWebsocketMessageType.ERROR;
            this.message = null;
            this.errorMessage = throwable.getMessage();
            this.errorTranslationKey = throwable instanceof IrisException irisException ? irisException.getTranslationKey() : null;
            this.translationParams = throwable instanceof IrisException irisException ? irisException.getTranslationParams() : null;
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

        public Map<String, Object> getTranslationParams() {
            return translationParams != null ? Collections.unmodifiableMap(translationParams) : null;
        }

        public enum IrisWebsocketMessageType {
            MESSAGE, ERROR
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            IrisWebsocketDTO that = (IrisWebsocketDTO) other;
            return type == that.type && Objects.equals(message, that.message) && Objects.equals(errorMessage, that.errorMessage)
                    && Objects.equals(errorTranslationKey, that.errorTranslationKey) && Objects.equals(translationParams, that.translationParams);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, message, errorMessage, errorTranslationKey, translationParams);
        }

        @Override
        public String toString() {
            return "IrisWebsocketDTO{" + "type=" + type + ", message=" + message + ", errorMessage='" + errorMessage + '\'' + ", errorTranslationKey='" + errorTranslationKey + '\''
                    + ", translationParams=" + translationParams + '}';
        }
    }
}
