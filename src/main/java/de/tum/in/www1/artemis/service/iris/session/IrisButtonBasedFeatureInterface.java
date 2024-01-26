package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

public interface IrisButtonBasedFeatureInterface<S extends IrisSession, O> extends IrisSubFeatureInterface<S> {

    /**
     * Sends a request to Iris to get an answer for the given session.
     *
     * @param input The input to send to Iris
     * @return The response created with Iris
     */
    O executeRequest(S input);
}
