package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextBlockType;

/**
 * DTO containing {@link TextBlock} information.
 * The associated feedback and submission are intentionally not exposed (mirrors the entity's {@code @JsonIgnore}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextBlockDTO(String id, String text, int startIndex, int endIndex, TextBlockType type) implements Serializable {

    public static TextBlockDTO of(TextBlock textBlock) {
        if (textBlock == null) {
            return null;
        }
        return new TextBlockDTO(textBlock.getId(), textBlock.getText(), textBlock.getStartIndex(), textBlock.getEndIndex(), textBlock.getType());
    }
}
