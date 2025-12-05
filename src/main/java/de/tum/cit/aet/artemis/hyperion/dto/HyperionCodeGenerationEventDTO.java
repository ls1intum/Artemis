package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionCodeGenerationEventDTO(Type type, String jobId, long exerciseId, Integer iteration, RepositoryType repositoryType, String path, Boolean success,
        Integer attempts, String message) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Type {
        STARTED, PROGRESS, FILE_UPDATED, NEW_FILE, DONE, ERROR
    }
}
