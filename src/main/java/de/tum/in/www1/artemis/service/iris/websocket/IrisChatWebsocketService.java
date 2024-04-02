package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;

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
     */
    public void sendMessage(IrisMessage irisMessage, List<PyrisStageDTO> stages) {
        var session = irisMessage.getSession();
        var user = checkSessionTypeAndGetUser(session);
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), new IrisWebsocketDTO(irisMessage, rateLimitInfo, stages));
    }

    /**
     * Sends an exception over the websocket to a specific user
     *
     * @param session   to which the exception belongs
     * @param throwable that should be sent over the websocket
     */
    public void sendException(IrisSession session, Throwable throwable, List<PyrisStageDTO> stages) {
        User user = checkSessionTypeAndGetUser(session);
        var rateLimitInfo = rateLimitService.getRateLimitInformation(user);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), new IrisWebsocketDTO(throwable, rateLimitInfo, stages));
    }

    public void sendStatusUpdate(IrisSession session, List<PyrisStageDTO> stages) {
        var user = checkSessionTypeAndGetUser(session);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), new IrisWebsocketDTO(rateLimitService.getRateLimitInformation(user), stages));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class IrisWebsocketDTO {

        private final IrisWebsocketMessageType type;

        private final IrisMessage message;

        private final String errorMessage;

        private final String errorTranslationKey;

        private final Map<String, Object> translationParams;

        private final IrisRateLimitService.IrisRateLimitInformation rateLimitInfo;

        private final List<PyrisStageDTO> stages;

        public IrisWebsocketDTO(IrisMessage message, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {
            this.rateLimitInfo = rateLimitInfo;
            this.stages = stages;
            this.type = IrisWebsocketMessageType.MESSAGE;
            this.message = message;
            this.errorMessage = null;
            this.errorTranslationKey = null;
            this.translationParams = null;
        }

        public IrisWebsocketDTO(Throwable throwable, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo, List<PyrisStageDTO> stages) {
            this.rateLimitInfo = rateLimitInfo;
            this.stages = stages;
            this.type = IrisWebsocketMessageType.ERROR;
            this.message = null;
            this.errorMessage = throwable.getMessage();
            this.errorTranslationKey = throwable instanceof IrisException irisException ? irisException.getTranslationKey() : null;
            this.translationParams = throwable instanceof IrisException irisException ? irisException.getTranslationParams() : null;
        }

        public IrisWebsocketDTO(IrisRateLimitService.IrisRateLimitInformation rateLimitInformation, List<PyrisStageDTO> stages) {
            this.rateLimitInfo = rateLimitInformation;
            this.stages = stages;
            this.type = IrisWebsocketMessageType.STATUS;
            this.message = null;
            this.errorMessage = null;
            this.errorTranslationKey = null;
            this.translationParams = null;
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

        public IrisRateLimitService.IrisRateLimitInformation getRateLimitInfo() {
            return rateLimitInfo;
        }

        public List<PyrisStageDTO> getStages() {
            return stages;
        }

        public enum IrisWebsocketMessageType {
            MESSAGE, STATUS, ERROR
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
            return Objects.hash(type, message, errorMessage, errorTranslationKey, translationParams, rateLimitInfo, stages);
        }

        @Override
        public String toString() {
            return "IrisWebsocketDTO{" + "type=" + type + ", message=" + message + ", errorMessage='" + errorMessage + '\'' + ", errorTranslationKey='" + errorTranslationKey + '\''
                    + ", translationParams=" + translationParams + ", rateLimitInfo=" + rateLimitInfo + ", stages=" + stages + '}';
        }
    }
}
