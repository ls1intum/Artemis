package de.tum.cit.aet.artemis.communication.dto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
    static class GroupChatCreationDeserializer extends JsonDeserializer<GroupChatCreationDTO> {

        @Override
        public GroupChatCreationDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.readValueAsTree();
            JsonNode loginsNode = node.isArray() ? node : node.get("memberLogins");
            List<String> memberLogins = new ArrayList<>();
            if (loginsNode != null && loginsNode.isArray()) {
                loginsNode.forEach(login -> memberLogins.add(login.asText()));
            }
            return new GroupChatCreationDTO(memberLogins);
        }
    }
}
