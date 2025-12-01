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
 * @param uncommittedFiles      optional map of uncommitted file changes (path to content)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageRequestDTO(@NonNull List<IrisMessageContent> content, @Nullable Integer messageDifferentiator, @Nullable Map<String, String> uncommittedFiles) {

    /**
     * Returns uncommitted files or empty map if null.
     *
     * @return Map of uncommitted files (path to content)
     */
    @Override
    public Map<String, String> uncommittedFiles() {
        return uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
