package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Tiny stateful coordinator that holds the "is the agent currently doing a maintenance action" flag.
 * <p>
 * Extracted into its own bean so {@link SharedQueueProcessingService} (the writer, flipping the flag around its
 * pause/resume cycle) and {@link BuildAgentInformationService} (the reader, deciding whether to emit
 * {@code BuildAgentStatus.MAINTENANCE}) can both depend on it without depending on each other. Without this
 * indirection both writer and reader would form a direct cycle, which Spring can only resolve via {@code @Lazy}
 * on a constructor parameter — exactly what the {@code ensureLazyAnnotationNotUsedOnParameters} architecture
 * rule forbids.
 * <p>
 * The flag is only meaningful while the agent is paused. Consumers should observe {@code isPaused()} on
 * {@link SharedQueueProcessingService} first; this class deliberately knows nothing about the broader paused
 * state to keep the dependency direction strictly one-way.
 */
@Service
@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
public class BuildAgentMaintenanceStateService {

    private final AtomicBoolean inMaintenance = new AtomicBoolean(false);

    /** @return {@code true} if a maintenance action (cache cleanup, wipe, Docker image clear) is currently running */
    public boolean isInMaintenance() {
        return inMaintenance.get();
    }

    /** @param inMaintenance the new flag value; only callers that own the maintenance pause should pass {@code true} */
    public void setInMaintenance(boolean inMaintenance) {
        this.inMaintenance.set(inMaintenance);
    }
}
