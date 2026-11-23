package de.tum.cit.aet.artemis.localvc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The fingerprint of one SSH host key of a node.
 *
 * @param algorithm   the algorithm of the public key, for example {@code RSA} or {@code Ed25519}
 * @param fingerprint the SHA-256 fingerprint of the public key, in the form {@code SHA256:...}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SshHostKeyFingerprintDTO(String algorithm, String fingerprint) {
}
