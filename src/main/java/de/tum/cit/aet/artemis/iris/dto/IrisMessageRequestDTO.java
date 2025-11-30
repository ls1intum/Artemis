package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;

/**
 * DTO for sending messages to Iris with optional uncommitted file changes.
 * This DTO supports both legacy format (flat message fields) and new format (wrapped in "message" field).
 * The uncommittedFiles field is always optional.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageRequestDTO(
        // Message content - can be at root level (legacy) or nested
        List<IrisMessageContent> content,
        Integer messageDifferentiator,
        
        // Optional uncommitted files (new feature)
        Map<String, String> uncommittedFiles) {

    /**
     * Returns uncommitted files or empty map if null
     *
     * @return Map of uncommitted files (path -> content)
     */
    public Map<String, String> uncommittedFiles() {
        return uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
