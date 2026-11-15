package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Artemis's answer to a worker heartbeat: the runs the worker listed that Artemis no longer tracks
 * as in flight (reclaimed lease, completed elsewhere, or deleted), so the worker can stop spending
 * resources on them. The write paths' per-run id sweeps keep even a zombie run harmless, but stopping
 * it early is cheaper.
 *
 * @param revokedRunIds    entries from the request, exactly as sent, that no longer belong to an in-flight run
 * @param revokedJobTokens the same list under its earlier name, for workers that read only that field
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWorkerHeartbeatResponseDTO(List<String> revokedRunIds, List<String> revokedJobTokens) {

    /**
     * @param revoked the revoked entries, exactly as the worker sent them
     * @return the response carrying them under both names
     */
    public static PyrisWorkerHeartbeatResponseDTO of(List<String> revoked) {
        return new PyrisWorkerHeartbeatResponseDTO(revoked, revoked);
    }
}
