package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Request body for creating a one-to-one chat. A one-to-one chat always has exactly one other participant, identified by exactly one of {@code userId} or {@code login}.
 * <p>
 * For backwards compatibility with deployed clients (iOS, Android, older web), a bare JSON array containing a single login (the legacy format
 * {@code ["partnerLogin"]}) is also accepted and mapped to {@code login}. The legacy array form should be removed once all clients send the object form.
 *
 * @param userId the id of the other participant (mutually exclusive with {@code login})
 * @param login  the login of the other participant (mutually exclusive with {@code userId})
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(using = OneToOneChatCreationDTO.OneToOneChatCreationDeserializer.class)
public record OneToOneChatCreationDTO(@Nullable Long userId, @Nullable String login) {

    /**
     * Accepts both the canonical object form ({@code {"userId": 1}} or {@code {"login": "ab12cde"}}) and the deprecated single-login array form ({@code ["ab12cde"]}).
     */
    static class OneToOneChatCreationDeserializer extends ValueDeserializer<OneToOneChatCreationDTO> {

        @Override
        public OneToOneChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                return readLegacyArray(parser, context);
            }
            Long userId = null;
            // the text of a userId that is not an integer id; a repeated property keeps its last value, so a later valid one clears it
            String invalidUserId = null;
            String login = null;
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String property = parser.currentName();
                    parser.nextToken();
                    if ("userId".equals(property)) {
                        userId = null;
                        invalidUserId = null;
                        if (parser.currentToken() != JsonToken.VALUE_NULL) {
                            // Require an integral JSON number in long range. A fractional number (e.g. 1.9) would be silently truncated to a different id, and a textual value
                            // would be coerced to 0L, so both are reported as an input mismatch below, which Spring maps to 400 Bad Request.
                            userId = readLongId(parser);
                            if (userId == null) {
                                invalidUserId = parser.getValueAsString("");
                                parser.skipChildren();
                            }
                        }
                    }
                    else if ("login".equals(property)) {
                        login = parser.currentToken() == JsonToken.VALUE_NULL ? null : parser.getValueAsString("");
                        parser.skipChildren();
                    }
                    else {
                        parser.skipChildren();
                    }
                }
            }
            if (invalidUserId != null) {
                return context.reportInputMismatch(OneToOneChatCreationDTO.class, "userId must be an integer id, but got: %s", invalidUserId);
            }
            return new OneToOneChatCreationDTO(userId, login);
        }

        /**
         * Reads the deprecated format: a single-element array of the partner login.
         *
         * @param parser  the parser, positioned on the start of the array
         * @param context the deserialization context
         * @return the one-to-one chat request naming the login
         */
        private static OneToOneChatCreationDTO readLegacyArray(JsonParser parser, DeserializationContext context) {
            int size = 0;
            String login = null;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (size == 0) {
                    // a non-string element is read as its text if it is a scalar and as null otherwise: Jackson 3 would throw where Jackson 2 returned ""
                    login = parser.getValueAsString(null);
                }
                size++;
                parser.skipChildren();
            }
            if (size != 1) {
                // Reported as an input mismatch so Spring maps it to 400 Bad Request (not 500).
                return context.reportInputMismatch(OneToOneChatCreationDTO.class, "A one-to-one chat must specify exactly one partner login, but got %d", size);
            }
            return new OneToOneChatCreationDTO(null, login);
        }

        /**
         * Reads the number the parser points at as an id.
         *
         * @param parser the parser, positioned on a value
         * @return the id, or {@code null} if the value is not an integral number in long range
         */
        private static Long readLongId(JsonParser parser) {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                Number number = parser.getNumberValue();
                if (number instanceof Integer || number instanceof Long) {
                    return number.longValue();
                }
            }
            return null;
        }
    }
}
