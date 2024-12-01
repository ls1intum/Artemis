package de.tum.cit.aet.artemis.iris.service.session;

import de.tum.cit.aet.artemis.core.domain.User;

public interface IrisRateLimitedFeatureInterface {

    void checkRateLimit(User user);
}
