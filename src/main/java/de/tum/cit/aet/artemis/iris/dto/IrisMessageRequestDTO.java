package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for sending messages to Iris with optional uncommitted file changes and optional context information.
 *
 * @param content               the message content
 * @param messageDifferentiator used to differentiate messages
 * @param uncommittedFiles      optional map of uncommitted file changes (path to content), defaults to empty map if null
 * @param context               optional list of context objects providing information about what the user is viewing (not persisted, only sent to Pyris)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageRequestDTO(@NonNull List<IrisMessageContentDTO> content, @Nullable Integer messageDifferentiator, @NonNull Map<String, String> uncommittedFiles,
        @Valid @Nullable List<IrisMessageContextDTO> context) {

    /**
     * Compact constructor that normalizes null uncommittedFiles to an empty map.
     */
    public IrisMessageRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
