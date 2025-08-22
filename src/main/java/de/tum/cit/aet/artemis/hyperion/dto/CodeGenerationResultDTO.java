package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for REST API code generation result.
 * Contains the result of the code generation and compilation process.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationResultDTO(
        /**
         * Whether the code generation and compilation was successful
         */
        boolean success,

        /**
         * Descriptive message about the generation result
         */
        String message,

        /**
         * Number of attempts made during the generation process
         */
        int attempts) {
}
