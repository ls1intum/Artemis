package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Deserializer for {@link Action} that determines the type of the action based on the content of the JSON.
 */
public class ActionDeserializer extends JsonDeserializer<Action> {

    @Override
    public Action deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);

        if (node.has("script")) {
            return jp.getCodec().treeToValue(node, ScriptAction.class);
        }
        else if (node.has("platform")) {
            return jp.getCodec().treeToValue(node, PlatformAction.class);
        }

        throw new JsonProcessingException("Cannot determine type") {
        };
    }
}
