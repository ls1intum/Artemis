package de.tum.cit.aet.artemis.service.connectors.aeolus;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Deserializer for {@link Action} that determines the type of the action based on the content of the JSON.
 */
public class ActionDeserializer extends JsonDeserializer<Action> {

    /**
     * Deserializes a JSON object into an {@link Action} object. This method determines the specific
     * type of {@code Action} to instantiate based on the content of the JSON object.
     *
     * <p>
     * If the JSON object contains a {@code class} field, this method uses its value to determine
     * the specific {@code Action} subclass to instantiate. If there is no {@code class} field, the
     * method looks for other specific fields (like {@code script} or {@code platform}) to infer the
     * action type. Currently, it supports deserialization into {@code ScriptAction} and
     * {@code PlatformAction} based on the presence of these fields.
     * </p>
     *
     * @param parser  the {@link JsonParser} used to parse the JSON content
     * @param context the {@link DeserializationContext} providing configuration and caching capabilities
     * @return an instance of a subclass of {@code Action}, specifically {@code ScriptAction} or
     *         {@code PlatformAction}, based on the JSON object content
     * @throws IOException if an input/output error occurs during parsing
     */
    @Override
    public Action deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        String className = "not-determined";
        if (node.has("class")) {
            className = node.get("class").asText();
        }
        else {
            if (node.has("script")) {
                className = "script-action";
            }
            else if (node.has("platform")) {
                className = "platform-action";
            }
        }
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        return switch (className) {
            case "script-action" -> mapper.treeToValue(node, ScriptAction.class);
            case "platform-action" -> mapper.treeToValue(node, PlatformAction.class);
            default -> throw new IOException("Cannot determine type");
        };
    }
}
