package de.tum.cit.aet.artemis.iris.api;

import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;

/**
 * Exposes whether the Iris service is actually answering, for callers outside the Iris module.
 * <p>
 * Only the reachability is exposed, not the health details: a consumer that just needs to say whether Iris is up should
 * not have to depend on the shape of the health payload.
 */
@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisHealthApi extends AbstractIrisApi {

    private final PyrisHealthIndicator pyrisHealthIndicator;

    public IrisHealthApi(PyrisHealthIndicator pyrisHealthIndicator) {
        this.pyrisHealthIndicator = pyrisHealthIndicator;
    }

    /**
     * Whether Iris is currently reachable. The underlying check is cached for {@code artemis.iris.health-ttl}, so this is
     * cheap enough to call on a page load.
     *
     * @return {@code true} if Iris answered its last health check
     */
    public boolean isReachable() {
        return pyrisHealthIndicator.health().getStatus() == Status.UP;
    }
}
