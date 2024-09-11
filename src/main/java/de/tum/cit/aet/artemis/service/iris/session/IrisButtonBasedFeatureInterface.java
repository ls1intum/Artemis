package de.tum.cit.aet.artemis.service.iris.session;

import de.tum.cit.aet.artemis.domain.iris.session.IrisSession;

public interface IrisButtonBasedFeatureInterface<S extends IrisSession, O> extends IrisSubFeatureInterface<S> {

    /**
     * Sends a request to Iris to get an answer for the given session.
     *
     * @param session The iris session used
     * @return The response created with Iris
     */
    O executeRequest(S session);
}
