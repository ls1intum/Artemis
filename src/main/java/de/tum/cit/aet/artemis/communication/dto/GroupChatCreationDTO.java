package de.tum.cit.aet.artemis.communication.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Request body for creating a group chat. {@code memberLogins} are the logins of the starting members (excluding the requesting user).
 * <p>
 * For backwards compatibility with deployed clients (iOS, Android, older web), a bare JSON array of logins (the legacy format {@code ["a", "b"]}) is also accepted and mapped to
 * {@code memberLogins}. The legacy array form should be removed once all clients send the object form.
 *
 * @param memberLogins the logins of the starting members of the group chat (excluding the requesting user)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(using = GroupChatCreationDTO.GroupChatCreationDeserializer.class)
public record GroupChatCreationDTO(List<String> memberLogins) {

    /**
     * Accepts both the canonical object form ({@code {"memberLogins": ["a", "b"]}}) and the deprecated bare-array form ({@code ["a", "b"]}).
     */
    static class GroupChatCreationDeserializer extends ValueDeserializer<GroupChatCreationDTO> {

        private static final String MEMBER_LOGINS_PROPERTY = "memberLogins";

        @Override
        public GroupChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) {
            List<String> memberLogins = new ArrayList<>();
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                memberLogins = readLogins(parser);
            }
            else if (parser.currentToken() == JsonToken.START_OBJECT) {
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String property = parser.currentName();
                    parser.nextToken();
                    if (MEMBER_LOGINS_PROPERTY.equals(property) && parser.currentToken() == JsonToken.START_ARRAY) {
                        memberLogins = readLogins(parser);
                    }
                    else {
                        parser.skipChildren();
                    }
                }
            }
            return new GroupChatCreationDTO(memberLogins);
        }

        /**
         * Reads the logins of the array the parser points at, leaving the parser on its closing token.
         * <p>
         * An element that is not a string is read as its text if it is a scalar, and as {@code null} otherwise. Jackson 3 would throw on a non-string element where Jackson 2
         * returned "", which would turn a bad request body into a 500.
         *
         * @param parser the parser, positioned on the start of the array
         * @return the logins in the array
         */
        private static List<String> readLogins(JsonParser parser) {
            List<String> logins = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                logins.add(parser.getValueAsString(null));
                parser.skipChildren();
            }
            return logins;
        }
    }
}
