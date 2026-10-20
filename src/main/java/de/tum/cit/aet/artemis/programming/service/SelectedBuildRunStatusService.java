package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Remembers for a while which runs that queue the builds of selected participations have finished.
 * <p>
 * The end of a run is announced with a websocket message, which a client that is disconnected at that moment never receives. The client
 * asks this service instead, when its connection is back and from time to time while it waits. The state is shared by all core nodes,
 * because the run may have ended on another node than the one that answers the question. It only has to outlive the time a client needs to
 * notice that it missed the message, so it expires.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class SelectedBuildRunStatusService {

    private static final String COMPLETED_RUNS = "selected-build-runs-completed";

    /**
     * Long enough for a client to come back from a lost connection or a sleeping device, short enough that the map stays small.
     */
    private static final Duration COMPLETED_RUN_TIME_TO_LIVE = Duration.ofHours(1);

    /**
     * The exercise of every completed run by the id of the run. The exercise is stored so that a run is only reported for the exercise it
     * belongs to.
     */
    private final DistributedMap<String, Long> completedRuns;

    public SelectedBuildRunStatusService(DistributedDataProvider distributedDataProvider) {
        this.completedRuns = distributedDataProvider.getExpiringMap(COMPLETED_RUNS, COMPLETED_RUN_TIME_TO_LIVE);
    }

    /**
     * Records that a run has queued its last build, or has failed to queue the rest.
     *
     * @param runId      the run that ended
     * @param exerciseId the exercise the run belongs to
     */
    public void markCompleted(String runId, long exerciseId) {
        completedRuns.put(runId, exerciseId);
    }

    /**
     * Whether a run of the exercise has ended and the server still remembers it.
     *
     * @param runId      the run to look up
     * @param exerciseId the exercise the caller asks about
     * @return true if the run ended and belongs to the exercise, false for a run that is still going on, unknown or forgotten
     */
    public boolean isCompleted(String runId, long exerciseId) {
        Long completedRunExerciseId = completedRuns.get(runId);
        return completedRunExerciseId != null && completedRunExerciseId == exerciseId;
    }
}
