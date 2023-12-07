package de.tum.in.www1.artemis.service.iris.session;

import java.util.Map;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

/**
 * Interface for Iris session sub services.
 * Each iris session sub service handles a specific type of Iris session.
 */
public interface IrisSessionSubServiceInterface {

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param message that should be sent over the websocket
     */
    void sendOverWebsocket(IrisMessage message);

    /**
     * Sends a request to Iris to get a message for the given session.
     *
     * @param irisSession  The session to get a message for
     * @param clientParams Extra parameters from the client for the request
     */
    void requestAndHandleResponse(IrisSession irisSession, Map<String, Object> clientParams);

    /**
     * Checks if the user has access to the Iris session.
     *
     * @param irisSession The session to check
     * @param user        The user to check
     */
    void checkHasAccessToIrisSession(IrisSession irisSession, User user);

    void checkRateLimit(User user);

    /**
     * Checks if the exercise connected to the session has Iris activated.
     *
     * @param irisSession The session to check
     */
    void checkIsIrisActivated(IrisSession irisSession);

    default <S extends IrisSession> S castToSessionType(IrisSession irisSession, Class<S> sessionClass) {
        if (!sessionClass.isInstance(irisSession)) {
            throw new IllegalStateException("IrisSession is not of type " + sessionClass.getSimpleName());
        }
        return sessionClass.cast(irisSession);
    }
}
