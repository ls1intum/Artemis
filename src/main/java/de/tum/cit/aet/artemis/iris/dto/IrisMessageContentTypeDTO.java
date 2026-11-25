package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;

/** Read only the discriminator before deserializing a structured message's type-specific fields. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageContentTypeDTO(@Nullable String type) {

    /**
     * Reads the discriminator of a structured content.
     *
     * @param content any message content
     * @return the {@code type} of a JSON object content, or null for text and for JSON of any other shape
     */
    public static @Nullable String typeOf(IrisMessageContent content) {
        if (!(content instanceof IrisJsonMessageContent json)) {
            return null;
        }
        try {
            var header = json.readAs(IrisMessageContentTypeDTO.class);
            return header == null ? null : header.type();
        }
        catch (JacksonException ignored) {
            return null;
        }
    }
}
