package de.tum.cit.aet.artemis.iris.dto;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageContentResponseDTO(@Nullable Long id, String type, @Nullable String textContent,
        @Nullable @JsonRawValue @JsonDeserialize(using = RawJsonDeserializer.class) String attributes) {

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

    /**
     * Deserializer that reads any JSON value as a raw string, complementing {@link JsonRawValue}
     * which only handles serialization.
     */
    static class RawJsonDeserializer extends JsonDeserializer<String> {

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.readValueAsTree().toString();
        }
    }
}
