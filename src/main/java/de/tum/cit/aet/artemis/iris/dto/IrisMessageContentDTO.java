package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

/**
 * DTO for IrisMessageContent to avoid mixing entities and DTOs in request objects.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageContentDTO(@Nullable String type, @Nullable String textContent, @Nullable String jsonContent) {

    /**
     * Converts this DTO to the corresponding entity.
     *
     * @return the entity representation of this DTO
     */
    public IrisMessageContent toEntity() {
        if ("json".equals(type)) {
            if (jsonContent == null) {
                throw new IllegalArgumentException("jsonContent must not be null for json message content");
            }
            var entity = new IrisJsonMessageContent();
            entity.setJsonContent(jsonContent);
            return entity;
        }
        // Default to text
        return new IrisTextMessageContent(textContent);
    }
}
