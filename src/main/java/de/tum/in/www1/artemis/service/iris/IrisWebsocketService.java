package de.tum.in.www1.artemis.service.iris;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
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
        if (!(irisMessage.getSession() instanceof IrisChatSession)) {
            throw new UnsupportedOperationException("Only IrisChatSession is supported");
        }
        Long irisSessionId = irisMessage.getSession().getId();
        String userLogin = ((IrisChatSession) irisMessage.getSession()).getUser().getLogin();
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
        if (!(irisSession instanceof IrisChatSession)) {
            throw new UnsupportedOperationException("Only IrisChatSession is supported");
        }
        Long irisSessionId = irisSession.getId();
        String userLogin = ((IrisChatSession) irisSession).getUser().getLogin();
        String irisWebsocketTopic = String.format("%s/sessions/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, irisSessionId);
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
