package de.tum.cit.aet.artemis.communication.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
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

        @Override
        public GroupChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = parser.readValueAsTree();
            JsonNode loginsNode = node.isArray() ? node : node.get("memberLogins");
            List<String> memberLogins = new ArrayList<>();
            if (loginsNode != null && loginsNode.isArray()) {
                // asString(null) on a client-supplied array: Jackson 3 throws on a non-string element where Jackson 2's
                // asText() returned "", which would turn a bad request body into a 500.
                loginsNode.forEach(login -> memberLogins.add(login.asString(null)));
            }
            return new GroupChatCreationDTO(memberLogins);
        }
    }
}
