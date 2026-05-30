package de.tum.cit.aet.artemis.communication.dto;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Request body for creating a one-to-one chat. A one-to-one chat always has exactly one other participant, identified by exactly one of {@code userId} or {@code login}.
 * <p>
 * For backwards compatibility with deployed clients (iOS, Android, older web), a bare JSON array containing a single login (the legacy format
 * {@code ["partnerLogin"]}) is also accepted and mapped to {@code login}. The legacy array form should be removed once all clients send the object form.
 *
 * @param userId the id of the other participant (mutually exclusive with {@code login})
 * @param login  the login of the other participant (mutually exclusive with {@code userId})
 */
@JsonDeserialize(using = OneToOneChatCreationDTO.OneToOneChatCreationDeserializer.class)
public record OneToOneChatCreationDTO(@Nullable Long userId, @Nullable String login) {

    /**
     * Accepts both the canonical object form ({@code {"userId": 1}} or {@code {"login": "ab12cde"}}) and the deprecated single-login array form ({@code ["ab12cde"]}).
     */
    static class OneToOneChatCreationDeserializer extends JsonDeserializer<OneToOneChatCreationDTO> {

        @Override
        public OneToOneChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.readValueAsTree();
            if (node.isArray()) {
                // Deprecated legacy format: a single-element array of the partner login.
                if (node.size() != 1) {
                    // Reported as an input mismatch so Spring maps it to 400 Bad Request (not 500).
                    return context.reportInputMismatch(OneToOneChatCreationDTO.class, "A one-to-one chat must specify exactly one partner login, but got %d", node.size());
                }
                return new OneToOneChatCreationDTO(null, node.get(0).asText());
            }
            Long userId = null;
            if (node.hasNonNull("userId")) {
                JsonNode userIdNode = node.get("userId");
                if (!userIdNode.canConvertToLong()) {
                    // Reject non-numeric / fractional userId as an input mismatch so Spring maps it to 400 Bad Request (asLong() would otherwise coerce "abc" to 0L).
                    return context.reportInputMismatch(OneToOneChatCreationDTO.class, "userId must be an integer id, but got: %s", userIdNode.asText());
                }
                userId = userIdNode.asLong();
            }
            String login = node.hasNonNull("login") ? node.get("login").asText() : null;
            return new OneToOneChatCreationDTO(userId, login);
        }
    }
}
