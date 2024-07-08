package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

public interface IrisChatBasedFeatureInterface<S extends IrisSession, E> extends IrisSubFeatureInterface<S> {

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param message that should be sent over the websocket
     */
    void sendOverWebsocket(IrisMessage message);

    /**
     * Sends a request to Iris to get a message for the given session.
     *
     * @param irisSession The session to get a message for
     */
    void requestAndHandleResponse(S irisSession);

    /**
     * Creates a new session for the given context and user.
     *
     * @param context            The context (e.g. an exercise) for which the session should be created
     * @param user               The user for which the session should be created
     * @param sendInitialMessage Whether an initial message should be sent by Iris
     * @return The created session
     */
    S createSession(E context, User user, boolean sendInitialMessage);
}
