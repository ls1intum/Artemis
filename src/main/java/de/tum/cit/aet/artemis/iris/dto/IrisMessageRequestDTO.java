package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;

/**
 * DTO for sending messages to Iris with optional uncommitted file changes.
 *
 * @param content               the message content
 * @param messageDifferentiator used to differentiate messages
 * @param uncommittedFiles      optional map of uncommitted file changes (path to content), defaults to empty map if null
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageRequestDTO(@NonNull List<IrisMessageContent> content, @Nullable Integer messageDifferentiator, @NonNull Map<String, String> uncommittedFiles) {

    /**
     * Compact constructor that normalizes null uncommittedFiles to an empty map.
     */
    public IrisMessageRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
