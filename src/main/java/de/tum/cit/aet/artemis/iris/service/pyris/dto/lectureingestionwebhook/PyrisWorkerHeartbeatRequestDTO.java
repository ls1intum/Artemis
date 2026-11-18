package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Pyris worker's fixed-interval liveness heartbeat. Deliberately decoupled from pipeline progress:
 * it lists which runs the worker process is executing right now, and its cadence is a timer rather
 * than the work, so the lease threshold never has to be sized to unpredictable AI-stage latency.
 *
 * @param bootId          boot id of the heartbeating worker process
 * @param activeRunIds    run ids of every run the worker is currently executing; may be empty
 * @param activeJobTokens the same list under its earlier name, which workers send for Artemis versions that do not know
 *                            {@code activeRunIds}; read only when {@code activeRunIds} is absent
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWorkerHeartbeatRequestDTO(String bootId, @Nullable List<String> activeRunIds, @Nullable List<String> activeJobTokens) {

    /**
     * @return the run ids the worker reported, from whichever field it sent
     */
    public List<String> reportedRunIds() {
        if (activeRunIds != null) {
            return activeRunIds;
        }
        return activeJobTokens != null ? activeJobTokens : List.of();
    }
}
