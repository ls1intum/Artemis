package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response of POST /api/hyperion/exercises/{exerciseId}/generate-variant — mirrors CodeGenerationJobStartDTO.
 *
 * @param jobId the created job id
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantJobStartDTO(String jobId) implements Serializable {
}
