package de.tum.cit.aet.artemis.communication.config;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import de.tum.cit.aet.artemis.communication.dto.GroupChatCreationDTO;

/** Reads the canonical object and legacy array formats without a JSON tree or scalar coercion. */
public class GroupChatCreationDeserializer extends ValueDeserializer<GroupChatCreationDTO> {

    @Override
    public GroupChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.isExpectedStartArrayToken()) {
            return new GroupChatCreationDTO(readLogins(parser, context));
        }
        if (!parser.isExpectedStartObjectToken()) {
            return context.reportInputMismatch(GroupChatCreationDTO.class, "Expected a group chat object or an array of logins");
        }
        List<String> logins = List.of();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            if ("memberLogins".equals(field)) {
                logins = parser.currentToken() == JsonToken.VALUE_NULL ? List.of() : readLogins(parser, context);
            }
            else {
                parser.skipChildren();
            }
        }
        return new GroupChatCreationDTO(logins);
    }

    private static List<String> readLogins(JsonParser parser, DeserializationContext context) {
        if (!parser.isExpectedStartArrayToken()) {
            return context.reportInputMismatch(GroupChatCreationDTO.class, "memberLogins must be an array of strings");
        }
        List<String> logins = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return context.reportInputMismatch(GroupChatCreationDTO.class, "memberLogins must contain only strings");
            }
            logins.add(parser.getString());
        }
        return logins;
    }
}
