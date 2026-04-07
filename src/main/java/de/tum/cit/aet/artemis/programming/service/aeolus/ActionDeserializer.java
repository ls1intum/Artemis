package de.tum.cit.aet.artemis.programming.service.aeolus;

import de.tum.cit.aet.artemis.programming.dto.aeolus.Action;
import de.tum.cit.aet.artemis.programming.dto.aeolus.PlatformAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializer for {@link Action} that determines the type of the action based on the content of the JSON.
 */
public class ActionDeserializer extends ValueDeserializer<Action> {

    /**
     * Deserializes a JSON object into an {@link Action} object. This method determines the specific
     * type of {@code Action} to instantiate based on the content of the JSON object.
     *
     * @param parser  the {@link JsonParser} used to parse the JSON content
     * @param context the {@link DeserializationContext} providing configuration and caching capabilities
     * @return an instance of a subclass of {@code Action}
     */
    @Override
    public Action deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        String className = "not-determined";
        if (node.has("class")) {
            className = node.get("class").stringValue();
        }
        else {
            if (node.has("script")) {
                className = "script-action";
            }
            else if (node.has("platform")) {
                className = "platform-action";
            }
        }
        return switch (className) {
            case "script-action" -> context.readTreeAsValue(node, ScriptAction.class);
            case "platform-action" -> context.readTreeAsValue(node, PlatformAction.class);
            default -> throw new JacksonException("Cannot determine type") {
            };
        };
    }
}
