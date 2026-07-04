package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

/**
 * Response of POST /api/hyperion/exercises/{exerciseId}/generate-variant (plan Section 5.1) —
 * mirrors CodeGenerationJobStartDTO.
 *
 * @param jobId the created (or, for the `active` reconnect endpoint, the running) job id
 */
public record VariantJobStartDTO(String jobId) implements Serializable {
}
