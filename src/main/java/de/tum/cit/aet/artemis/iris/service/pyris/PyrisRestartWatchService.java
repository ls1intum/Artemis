package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateRecoveryApi;

/**
 * Detects Pyris process restarts via the boot id it reports in its health response.
 * <p>
 * A Pyris restart kills every in-flight ingestion job without a terminal callback. The existing
 * DOWN-to-UP detection only sees restarts that happen to straddle two health checks; a fast restart
 * between two UP observations is invisible to it. The boot id is a fresh UUID per Pyris process, so
 * any change proves a restart happened, no matter how fast. On a detected change every in-flight
 * ingestion job is reset to IDLE for immediate re-dispatch, without spending retry budget.
 * <p>
 * The last observed boot id is cluster-wide state and lives in the distributed data provider, so any
 * node that observes a health response participates in detection and duplicate resets are prevented.
 */
@Conditional(IrisEnabled.class)
@Service
@Lazy
public class PyrisRestartWatchService {

    private static final Logger log = LoggerFactory.getLogger(PyrisRestartWatchService.class);

    private static final String MAP_NAME = "pyris-restart-watch";

    private static final String BOOT_ID_KEY = "bootId";

    private final DistributedDataProvider distributedDataProvider;

    private final Optional<ProcessingStateRecoveryApi> processingStateRecoveryApi;

    @Nullable
    private DistributedMap<String, String> bootIdMap;

    public PyrisRestartWatchService(DistributedDataProvider distributedDataProvider, Optional<ProcessingStateRecoveryApi> processingStateRecoveryApi) {
        this.distributedDataProvider = distributedDataProvider;
        this.processingStateRecoveryApi = processingStateRecoveryApi;
    }

    private DistributedMap<String, String> getBootIdMap() {
        if (bootIdMap == null) {
            bootIdMap = distributedDataProvider.getMap(MAP_NAME);
        }
        return bootIdMap;
    }

    /**
     * Record the boot id from a Pyris health response and reset in-flight jobs when it changed.
     * <p>
     * A missing boot id (older Pyris version) is a no-op. The first observed boot id is only stored,
     * because there is no previous value to compare against. Detection, recovery and publication happen
     * under one cluster-wide lock, and the new boot id is published only after the recovery succeeded:
     * a node that dies or fails mid-reset leaves the previous id in place, so the next observation on
     * any node retries the reset instead of losing the restart signal.
     *
     * @param bootId the boot id reported by Pyris, may be null
     */
    public void observeBootId(@Nullable String bootId) {
        if (bootId == null || bootId.isBlank()) {
            return;
        }

        var map = getBootIdMap();
        map.lock(BOOT_ID_KEY);
        try {
            String previousBootId = map.get(BOOT_ID_KEY);
            if (bootId.equals(previousBootId)) {
                return;
            }
            if (previousBootId == null) {
                map.put(BOOT_ID_KEY, bootId);
                log.info("Observed Pyris boot id for the first time");
                return;
            }

            log.warn("Pyris boot id changed — the process restarted, resetting in-flight ingestion jobs");
            try {
                processingStateRecoveryApi.ifPresent(api -> api.handleIrisReset());
            }
            catch (Exception e) {
                log.error("Failed to reset in-flight jobs after a Pyris boot id change; keeping the previous boot id so the next observation retries", e);
                return;
            }
            map.put(BOOT_ID_KEY, bootId);
        }
        finally {
            map.unlock(BOOT_ID_KEY);
        }
    }
}
