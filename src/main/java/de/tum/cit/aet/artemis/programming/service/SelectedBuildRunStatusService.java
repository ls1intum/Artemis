package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Remembers which runs that queue the builds of selected participations are going on.
 * <p>
 * The end of a run is announced with a websocket message, which a client that is disconnected at that moment never receives. The client
 * asks this service instead, when its connection is back and from time to time while it waits. The state is shared by all core nodes,
 * because the run may be going on at another node than the one that answers the question.
 * <p>
 * Only the runs that are going on are stored, and a run that is not stored counts as ended. That way a client that comes back after any
 * time away finds out that its run is over, whether the server remembers the run or not. The entries expire, so a run that never reports
 * its end, because its node died, does not stay active for ever.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class SelectedBuildRunStatusService {

    private static final Logger log = LoggerFactory.getLogger(SelectedBuildRunStatusService.class);

    private static final String ACTIVE_RUNS = "selected-build-runs-active";

    /**
     * Longer than a run can wait for room in the build queue and than it needs to queue its builds, so that an entry normally ends
     * with the run. If a run does outlast it, its client becomes available again early, which at worst allows a duplicate rebuild.
     */
    private static final Duration ACTIVE_RUN_TIME_TO_LIVE = Duration.ofHours(24);

    /**
     * The exercise of every active run by the id of the run. The exercise is stored so that a run is only reported for the exercise it
     * belongs to.
     */
    private final DistributedMap<String, Long> activeRuns;

    public SelectedBuildRunStatusService(DistributedDataProvider distributedDataProvider) {
        this.activeRuns = distributedDataProvider.getExpiringMap(ACTIVE_RUNS, ACTIVE_RUN_TIME_TO_LIVE);
    }

    /**
     * Records that a run is going on. A store that cannot be written is logged and otherwise ignored: the run must not fail for it, and a
     * client that asks for the status then finds the run ended, which at worst makes its retry action available early.
     *
     * @param runId      the run that starts
     * @param exerciseId the exercise the run belongs to
     */
    public void markStarted(String runId, long exerciseId) {
        try {
            activeRuns.put(runId, exerciseId);
        }
        catch (RuntimeException e) {
            log.warn("Could not record that the run {} of the exercise {} started, clients that miss its end cannot find out", runId, exerciseId, e);
        }
    }

    /**
     * Records that a run has queued its last build, or has failed to queue the rest. A store that cannot be written is logged and otherwise
     * ignored, because the message that announces the end must still be sent.
     *
     * @param runId the run that ended
     */
    public void markEnded(String runId) {
        try {
            activeRuns.remove(runId);
        }
        catch (RuntimeException e) {
            log.warn("Could not record that the run {} ended, clients that miss its end stay busy until the entry expires", runId, e);
        }
    }

    /**
     * Whether a run of the exercise is going on.
     *
     * @param runId      the run to look up
     * @param exerciseId the exercise the caller asks about
     * @return true if the run is going on and belongs to the exercise, false for a run that ended, is unknown or is forgotten
     */
    public boolean isActive(String runId, long exerciseId) {
        Long activeRunExerciseId = activeRuns.get(runId);
        return activeRunExerciseId != null && activeRunExerciseId == exerciseId;
    }
}
