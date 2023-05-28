package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.IrisSession;

/**
 * Interface for Iris session sub services.
 * Each iris session sub service handles a specific type of Iris session.
 */
public interface IrisSessionSubServiceInterface {

    /**
     * Sends a request to Iris to get a message for the given session.
     *
     * @param irisSession The session to get a message for
     */
    void requestAndHandleResponse(IrisSession irisSession);

    /**
     * Checks if the user has access to the Iris session.
     *
     * @param irisSession The session to check
     * @param user        The user to check
     */
    void checkHasAccessToIrisSession(IrisSession irisSession, User user);

    default <S extends IrisSession> S castToSessionType(IrisSession irisSession, Class<S> sessionClass) {
        if (!sessionClass.isInstance(irisSession)) {
            throw new IllegalStateException("IrisSession is not of type " + sessionClass.getSimpleName());
        }
        return sessionClass.cast(irisSession);
    }
}
