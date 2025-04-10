package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

/**
 * DTO for passkey information.
 *
 * @param credentialId encoded as base64url
 */
public record PasskeyDTO(String credentialId, String label, Instant created, Instant lastUsed) {
}
