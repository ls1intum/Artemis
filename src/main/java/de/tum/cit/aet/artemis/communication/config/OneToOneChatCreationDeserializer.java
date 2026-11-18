package de.tum.cit.aet.artemis.communication.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import de.tum.cit.aet.artemis.communication.dto.OneToOneChatCreationDTO;
import de.tum.cit.aet.artemis.core.config.StrictLongDeserializer;
import de.tum.cit.aet.artemis.core.config.StrictStringDeserializer;

/** Reads object requests and legacy single-login arrays, preserving strict integer-id validation. */
public class OneToOneChatCreationDeserializer extends ValueDeserializer<OneToOneChatCreationDTO> {

    private static final StrictLongDeserializer STRICT_LONG = new StrictLongDeserializer();

    private static final StrictStringDeserializer STRICT_STRING = new StrictStringDeserializer();

    @Override
    public OneToOneChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.isExpectedStartArrayToken()) {
            if (parser.nextToken() != JsonToken.VALUE_STRING) {
                return context.reportInputMismatch(OneToOneChatCreationDTO.class, "A one-to-one chat must specify exactly one partner login");
            }
            String login = parser.getString();
            if (parser.nextToken() != JsonToken.END_ARRAY) {
                return context.reportInputMismatch(OneToOneChatCreationDTO.class, "A one-to-one chat must specify exactly one partner login");
            }
            return new OneToOneChatCreationDTO(null, login);
        }
        if (!parser.isExpectedStartObjectToken()) {
            return context.reportInputMismatch(OneToOneChatCreationDTO.class, "Expected a one-to-one chat object or an array with one login");
        }
        Long userId = null;
        String login = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "userId" -> userId = parser.currentToken() == JsonToken.VALUE_NULL ? null : STRICT_LONG.deserialize(parser, context);
                case "login" -> login = parser.currentToken() == JsonToken.VALUE_NULL ? null : STRICT_STRING.deserialize(parser, context);
                default -> parser.skipChildren();
            }
        }
        return new OneToOneChatCreationDTO(userId, login);
    }
}
