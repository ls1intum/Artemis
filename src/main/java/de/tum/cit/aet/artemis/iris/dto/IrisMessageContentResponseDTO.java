package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.RawJsonStringDeserializer;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageContentResponseDTO(@Nullable Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type, @Nullable String textContent,
        @Schema(implementation = Object.class, types = {
                "object" }, additionalProperties = Schema.AdditionalPropertiesValue.TRUE, description = "Raw JSON attributes of this message content.") @Nullable @JsonRawValue @JsonDeserialize(using = RawJsonStringDeserializer.class) String attributes){

    /**
     * Creates a response DTO from an {@link IrisMessageContent} entity.
     *
     * @param content the message content entity to convert
     * @return the corresponding response DTO
     */
    public static IrisMessageContentResponseDTO of(IrisMessageContent content) {
        if (content instanceof IrisTextMessageContent text) {
            return new IrisMessageContentResponseDTO(content.getId(), "text", text.getTextContent(), null);
        }
        else if (content instanceof IrisJsonMessageContent json) {
            return new IrisMessageContentResponseDTO(content.getId(), "json", null, json.getAttributes());
        }
        throw new IllegalArgumentException("Unknown content type: " + content.getClass());
    }
}
