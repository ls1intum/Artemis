package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

public interface IrisSubFeatureInterface<S extends IrisSession> {

    /**
     * Checks if the user has access to the Iris session.
     *
     * @param user        The user to check
     * @param irisSession The session to check
     */
    void checkHasAccessTo(User user, S irisSession);

    /**
     * Checks if the feature is active for the context (e.g. an exercise) of the session.
     *
     * @param irisSession The session to check
     */
    void checkIsFeatureActivatedFor(S irisSession);
}
