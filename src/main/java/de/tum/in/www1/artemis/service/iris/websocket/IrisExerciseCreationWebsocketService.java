package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseCreationSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.session.exercisecreation.IrisExerciseUpdateDTO;

@Service
@Profile("iris")
public class IrisExerciseCreationWebsocketService extends IrisWebsocketService {

    private static final String WEBSOCKET_TOPIC_SESSION_TYPE = "exercise-creation-sessions";

    public IrisExerciseCreationWebsocketService(WebsocketMessagingService websocketMessagingService) {
        super(websocketMessagingService);
    }

    private User checkSessionTypeAndGetUser(IrisSession session) {
        if (!(session instanceof IrisExerciseCreationSession exerciseCreationSession)) {
            throw new UnsupportedOperationException("Only IrisExerciseCreationSession is supported");
        }
        return exerciseCreationSession.getUser();
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param message that should be sent over the websocket
     */
    public void sendMessage(IrisMessage message, IrisExerciseUpdateDTO exerciseUpdate) {
        var session = message.getSession();
        var user = checkSessionTypeAndGetUser(session);
        super.send(user, WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), IrisWebsocketDTO.message(message, exerciseUpdate));
    }

    /**
     * Sends an exception over the websocket to a specific user
     *
     * @param session   to which the exception belongs
     * @param throwable that should be sent over the websocket
     */
    public void sendException(IrisExerciseCreationSession session, Throwable throwable) {
        super.send(session.getUser(), WEBSOCKET_TOPIC_SESSION_TYPE, session.getId(), IrisWebsocketDTO.error(throwable));
    }

    public enum IrisWebsocketMessageType {
        MESSAGE, ERROR
    }

    public record IrisWebsocketDTO(IrisWebsocketMessageType type, IrisMessage message, IrisExerciseUpdateDTO exerciseUpdate, String errorMessage, String errorTranslationKey,
            Map<String, Object> translationParams) {

        private static IrisWebsocketDTO message(IrisMessage message, IrisExerciseUpdateDTO exerciseUpdate) {
            return new IrisWebsocketDTO(IrisWebsocketMessageType.MESSAGE, message, exerciseUpdate, null, null, null);
        }

        private static IrisWebsocketDTO error(Throwable throwable) {
            return new IrisWebsocketDTO(IrisWebsocketMessageType.ERROR, null, null, throwable.getMessage(), throwable instanceof IrisException i ? i.getTranslationKey() : null,
                    throwable instanceof IrisException i ? i.getTranslationParams() : null);
        }

    }

}
