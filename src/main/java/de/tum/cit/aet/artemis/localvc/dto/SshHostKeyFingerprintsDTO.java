package de.tum.cit.aet.artemis.localvc.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The fingerprints of the SSH host keys that a node publishes for the build agents that clone from it.
 *
 * @param hostKeys one entry per host key, so also several entries of one algorithm while a key is being replaced
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SshHostKeyFingerprintsDTO(List<SshHostKeyFingerprintDTO> hostKeys) {
}
