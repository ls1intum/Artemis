package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionCodeGenerationEventDTO(Type type, String jobId, long exerciseId, Integer iteration, RepositoryType repositoryType, String path, Boolean success,
        CompletionStatus completionStatus, CompletionReason completionReason, Map<String, String> completionReasonParams, Integer attempts, String message) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Type {
        STARTED, PROGRESS, FILE_UPDATED, NEW_FILE, DONE, ERROR
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum CompletionStatus {
        SUCCESS, PARTIAL, ERROR
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum CompletionReason {
        BUILD_SUCCEEDED, NO_COMMITTED_FILES, BUILD_FAILED, BUILD_TIMED_OUT, PARTICIPATION_NOT_FOUND, CI_TRIGGER_FAILED
    }
}
