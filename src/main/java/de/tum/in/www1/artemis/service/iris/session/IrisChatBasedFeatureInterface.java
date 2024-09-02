package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

public interface IrisChatBasedFeatureInterface<S extends IrisSession> extends IrisSubFeatureInterface<S> {

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param session that the message belongs to
     * @param message that should be sent over the websocket
     */
    void sendOverWebsocket(S session, IrisMessage message);

    /**
     * Sends a request to Iris to get a message for the given session.
     *
     * @param irisSession The session to get a message for
     */
    void requestAndHandleResponse(S irisSession);
}
