package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Artemis's answer to a worker heartbeat: the runs the worker listed that Artemis no longer tracks
 * as in flight (reclaimed lease, completed elsewhere, or deleted), so the worker can stop spending
 * resources on them. The write paths' per-run id sweeps keep even a zombie run harmless, but stopping
 * it early is cheaper.
 *
 * @param revokedJobTokens tokens from the request that no longer belong to an in-flight run
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWorkerHeartbeatResponseDTO(List<String> revokedJobTokens) {
}
