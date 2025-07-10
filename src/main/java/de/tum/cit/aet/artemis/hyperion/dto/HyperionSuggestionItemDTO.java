package de.tum.cit.aet.artemis.hyperion.dto;

import de.tum.cit.aet.artemis.hyperion.proto.SuggestionItem;

/**
 * DTO for suggestion items in WebSocket messages.
 */
public record HyperionSuggestionItemDTO(String description, int indexStart, int indexEnd, String priority) {

    /**
     * Creates a DTO from a proto SuggestionItem.
     */
    public static HyperionSuggestionItemDTO fromProto(SuggestionItem item) {
        return new HyperionSuggestionItemDTO(item.getDescription(), item.getIndexStart(), item.getIndexEnd(), item.getPriority().name());
    }
}
