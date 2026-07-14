package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

/**
 * Response of POST /api/hyperion/exercises/{exerciseId}/generate-variant — mirrors CodeGenerationJobStartDTO.
 *
 * @param jobId the created job id
 */
public record VariantJobStartDTO(String jobId) implements Serializable {
}
