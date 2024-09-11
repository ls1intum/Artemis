package de.tum.cit.aet.artemis.service.iris.session;

import de.tum.cit.aet.artemis.domain.User;

public interface IrisRateLimitedFeatureInterface {

    void checkRateLimit(User user);
}
