package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Polls Pyris health on a fixed schedule so restarts are detected promptly.
 * <p>
 * Without this, health is only fetched when a user or Actuator happens to ask for it, so a Pyris
 * restart could go unnoticed for a long time and in-flight ingestion jobs would wait for the
 * staleness timeout instead of being re-dispatched immediately. The health indicator itself feeds
 * every response's boot id into {@link PyrisRestartWatchService}, so this scheduler only has to
 * guarantee a regular observation.
 */
@Conditional(IrisEnabled.class)
@Profile(PROFILE_SCHEDULING)
@Component
@Lazy
public class PyrisRestartWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PyrisRestartWatchScheduler.class);

    private final PyrisHealthIndicator pyrisHealthIndicator;

    public PyrisRestartWatchScheduler(PyrisHealthIndicator pyrisHealthIndicator) {
        this.pyrisHealthIndicator = pyrisHealthIndicator;
    }

    /**
     * Fetch Pyris health every minute. The health indicator handles restart detection as a side
     * effect of parsing the response; a failed fetch is already logged and reflected there.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void pollPyrisHealth() {
        try {
            pyrisHealthIndicator.health(true);
        }
        catch (Exception e) {
            log.debug("Scheduled Pyris health poll failed: {}", e.getMessage());
        }
    }
}
