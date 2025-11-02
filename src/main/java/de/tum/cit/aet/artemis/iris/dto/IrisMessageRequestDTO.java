package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

/**
 * DTO for sending messages to Iris with optional uncommitted file changes
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageRequestDTO(IrisMessage message, Map<String, String> uncommittedFiles) {

    /**
     * Constructor for backward compatibility - creates DTO with empty uncommitted files
     *
     * @param message The Iris message
     */
    public IrisMessageRequestDTO(IrisMessage message) {
        this(message, Map.of());
    }

    /**
     * Returns uncommitted files or empty map if null
     *
     * @return Map of uncommitted files (path -> content)
     */
    public Map<String, String> uncommittedFiles() {
        return uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
