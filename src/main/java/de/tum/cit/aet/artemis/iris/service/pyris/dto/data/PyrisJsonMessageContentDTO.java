package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.RawJsonStringDeserializer;

/**
 * The JSON content of a message as Pyris receives it. {@code jsonContent} is embedded as JSON rather than as a quoted string.
 * <p>
 * {@link JsonRawValue} only governs serialization. Without a matching deserializer, Jackson would try to read the embedded JSON back into a {@link String} and fail with
 * "Cannot deserialize value of type String from Object value". Artemis only ever serializes this DTO when sending a request to Pyris, but tests (and any future consumer)
 * deserialize it, so the DTO must round-trip, which {@link RawJsonStringDeserializer} does.
 *
 * @param jsonContent the JSON text of the content
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisJsonMessageContentDTO(@JsonRawValue @JsonDeserialize(using = RawJsonStringDeserializer.class) String jsonContent) implements PyrisMessageContentBaseDTO {
}
