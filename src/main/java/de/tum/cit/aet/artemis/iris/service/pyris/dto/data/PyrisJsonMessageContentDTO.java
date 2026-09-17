package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisJsonMessageContentDTO(@JsonRawValue @JsonDeserialize(using = PyrisJsonMessageContentDTO.RawJsonDeserializer.class) String jsonContent)
        implements PyrisMessageContentBaseDTO {

    /**
     * Reads the embedded JSON subtree of {@code jsonContent} back into its string form.
     * <p>
     * {@link JsonRawValue} only governs serialization — it writes the string as embedded JSON rather than a quoted
     * string. Without a matching deserializer, Jackson would try to read that JSON object back into a {@link String}
     * and fail with "Cannot deserialize value of type String from Object value". Artemis only ever serializes this DTO
     * when sending a request to Pyris, but tests (and any future consumer) deserialize it, so the DTO must round-trip.
     */
    static class RawJsonDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = parser.readValueAsTree();
            return node.toString();
        }
    }
}
