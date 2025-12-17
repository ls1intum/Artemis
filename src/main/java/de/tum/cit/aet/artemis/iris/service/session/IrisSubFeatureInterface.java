package de.tum.cit.aet.artemis.iris.service.session;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

public interface IrisSubFeatureInterface<S extends IrisSession> {

    /**
     * Checks if the user has access to the Iris session.
     *
     * @param user        The user to check
     * @param irisSession The session to check
     */
    void checkHasAccessTo(User user, S irisSession);

    /**
     * Checks if Iris is active for the context (e.g. an exercise in a course) of the session.
     *
     * @param irisSession The session to check
     */
    void checkIrisEnabledFor(S irisSession);
}
