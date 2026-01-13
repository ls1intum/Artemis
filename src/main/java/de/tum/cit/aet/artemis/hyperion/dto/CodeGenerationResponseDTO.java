package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for internal code generation response.
 * Contains the generated content from AI service calls.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationResponseDTO(
        /**
         * The solution plan as a string
         */
        String solutionPlan,

        /**
         * List of generated files with path and content
         */
        List<GeneratedFileDTO> files) {

    public String getSolutionPlan() {
        return solutionPlan;
    }

    public List<GeneratedFileDTO> getFiles() {
        return files;
    }
}
