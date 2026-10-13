package de.tum.cit.aet.artemis.metis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;

/**
 * The poller that drives the generation of the Metis questions on the scheduling node.
 * <p>
 * Every {@code artemis.metis.generation.poll-interval-seconds} it runs one tick of {@link MetisGenerationDispatchService#dispatch} as the system. A tick reads
 * due dates, individual due dates and configurations from the database again, so neither an exercise update nor a saved configuration needs to reschedule
 * anything, on this node or another one. The first tick after the node started first puts the checks that a crash or a shutdown left in generation back into
 * the queue; if that fails, the next tick tries again. A failing tick is logged and the next one runs as usual.
 * <p>
 * Only the scheduling node has this bean, and no other bean injects it. Tests switch it off with {@code scheduler-enabled: false} and call the dispatcher
 * directly.
 */
@Lazy
@Component
@Conditional(MetisEnabled.class)
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class MetisGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetisGenerationScheduler.class);

    private final MetisProperties properties;

    private final MetisGenerationDispatchService dispatchService;

    /** Whether the next tick is the first since the node started, which resets the dispatcher first. */
    private final AtomicBoolean firstTick = new AtomicBoolean(true);

    public MetisGenerationScheduler(MetisProperties properties, MetisGenerationDispatchService dispatchService) {
        this.properties = properties;
        this.dispatchService = dispatchService;
    }

    /**
     * One tick of the poller: on the first tick the reset after the startup, then the sweeps and claims of the dispatcher, all as the system.
     */
    @Scheduled(initialDelay = 60, fixedDelayString = "${artemis.metis.generation.poll-interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void tick() {
        if (!properties.generation().schedulerEnabled()) {
            return;
        }
        SecurityUtils.runAsSystem(() -> {
            try {
                if (firstTick.get()) {
                    // Retried on the next tick if it throws
                    dispatchService.resetAfterStartup();
                    firstTick.set(false);
                }
                dispatchService.dispatch(TimeUtil.now());
            }
            catch (Exception e) {
                log.error("Metis generation tick failed", e);
            }
        });
    }
}
