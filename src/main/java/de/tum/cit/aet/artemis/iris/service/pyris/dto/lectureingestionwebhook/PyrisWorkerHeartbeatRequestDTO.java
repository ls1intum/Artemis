package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Pyris worker's fixed-interval liveness heartbeat. Deliberately decoupled from pipeline progress:
 * it lists which runs the worker process is executing right now, and its cadence is a timer rather
 * than the work, so the lease threshold never has to be sized to unpredictable AI-stage latency.
 *
 * @param bootId          boot id of the heartbeating worker process
 * @param activeJobTokens job tokens of every run the worker is currently executing; may be empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWorkerHeartbeatRequestDTO(String bootId, List<String> activeJobTokens) {
}
