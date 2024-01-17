package de.tum.in.www1.artemis.service.iris.session;

import de.tum.in.www1.artemis.domain.User;

public interface IrisRateLimitedFeatureInterface {

    void checkRateLimit(User user);
}
