package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

/**
 * DTO for IrisMessageContent to avoid mixing entities and DTOs in request objects.
 * Supports polymorphic deserialization for text and json content types.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = IrisMessageContentDTO.TextContent.class, name = "text"),
        @JsonSubTypes.Type(value = IrisMessageContentDTO.JsonContent.class, name = "json"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public sealed interface IrisMessageContentDTO permits IrisMessageContentDTO.TextContent, IrisMessageContentDTO.JsonContent {

    /**
     * Converts this DTO to the corresponding entity.
     *
     * @return the entity representation of this DTO
     */
    IrisMessageContent toEntity();

    /**
     * DTO for text message content.
     *
     * @param textContent the text content of the message
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record TextContent(@Nullable String textContent) implements IrisMessageContentDTO {

        @Override
        public IrisMessageContent toEntity() {
            return new IrisTextMessageContent(textContent);
        }
    }

    /**
     * DTO for JSON message content.
     *
     * @param jsonContent the JSON content of the message
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record JsonContent(@Nullable String jsonContent) implements IrisMessageContentDTO {

        @Override
        public IrisMessageContent toEntity() {
            var entity = new IrisJsonMessageContent();
            entity.setJsonContent(jsonContent);
            return entity;
        }
    }
}
