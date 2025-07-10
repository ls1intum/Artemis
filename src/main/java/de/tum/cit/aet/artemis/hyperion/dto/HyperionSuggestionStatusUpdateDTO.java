package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for WebSocket messages about suggestion improvements status.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionSuggestionStatusUpdateDTO(String type, HyperionSuggestionItemDTO suggestion, boolean isCompleted, String error) {

    /**
     * Creates a DTO for a new suggestion item.
     */
    public static HyperionSuggestionStatusUpdateDTO ofSuggestion(HyperionSuggestionItemDTO suggestion) {
        return new HyperionSuggestionStatusUpdateDTO("suggestion", suggestion, false, null);
    }

    /**
     * Creates a DTO for completion.
     */
    public static HyperionSuggestionStatusUpdateDTO ofCompletion() {
        return new HyperionSuggestionStatusUpdateDTO("completed", null, true, null);
    }

    /**
     * Creates a DTO for an error.
     */
    public static HyperionSuggestionStatusUpdateDTO ofError(String error) {
        return new HyperionSuggestionStatusUpdateDTO("error", null, false, error);
    }
}
