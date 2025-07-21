package de.tum.cit.aet.artemis.programming.service.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Hyperion solution generation response
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionSolutionGenerationResponse(@JsonProperty("repository") Repository repository, Metadata metadata) {

    /**
     * Repository information containing generated files
     */
    public record Repository(List<HyperionRepositoryFile> files) {
    }

    /**
     * Metadata for the generation response
     */
    public record Metadata(@JsonProperty("trace_id") String traceId) {
    }

    /**
     * Convenience constructor that takes files and trace ID directly
     */
    public HyperionSolutionGenerationResponse(List<HyperionRepositoryFile> files, String traceId) {
        this(new Repository(files), new Metadata(traceId));
    }
}
