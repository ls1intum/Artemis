package de.tum.cit.aet.artemis.iris.service.session;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

public interface IrisRateLimitedFeatureInterface<S extends IrisSession> {

    void checkRateLimit(User user, S session);
}
