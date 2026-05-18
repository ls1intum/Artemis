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
         * The plan as a string for the current generation stage (solution, test, or template).
         */
        String solutionPlan,

        /**
         * List of generated files with path and content
         */
        List<GeneratedFileDTO> files,

        /**
         * Source files that are obsolete after this generation and should be removed.
         */
        List<String> deletedFiles) {

    public CodeGenerationResponseDTO(String solutionPlan, List<GeneratedFileDTO> files) {
        this(solutionPlan, files, List.of());
    }

    public CodeGenerationResponseDTO {
        files = files != null ? files : List.of();
        deletedFiles = deletedFiles != null ? deletedFiles : List.of();
    }

    public String getSolutionPlan() {
        return solutionPlan;
    }

    public List<GeneratedFileDTO> getFiles() {
        return files;
    }

    public List<String> getDeletedFiles() {
        return deletedFiles;
    }
}
