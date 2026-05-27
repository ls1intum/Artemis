package de.tum.cit.aet.artemis.communication.domain;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Deserializes the reduced user representation embedded in reaction responses.
 */
public class ReactionUserDeserializer extends JsonDeserializer<User> {

    @Override
    public User deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }

        User user = new User();
        JsonNode id = node.get("id");
        if (id != null && id.canConvertToLong()) {
            user.setId(id.longValue());
        }

        JsonNode name = node.get("name");
        if (name != null && !name.isNull()) {
            user.setFirstName(name.asText());
        }
        return user;
    }
}
