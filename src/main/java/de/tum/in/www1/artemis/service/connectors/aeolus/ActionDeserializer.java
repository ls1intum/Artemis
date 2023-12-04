package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Deserializer for {@link Action} that determines the type of the action based on the content of the JSON.
 */
public class ActionDeserializer implements JsonDeserializer<Action> {

    @Override
    public Action deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String className = "not-determined";
        /*
         * If we receive the serialized form of an action it could have a "class" field that helps in determining
         * the actual type of the action. If it does not have this field we have to determine the type based on
         * other fields that are present.
         */
        if (jsonObject.has("class")) {
            className = jsonObject.get("class").getAsString();
        }
        else {
            if (jsonObject.has("script")) {
                className = "script-action";
            }
            else if (jsonObject.has("platform")) {
                className = "platform-action";
            }
        }
        return switch (className) {
            case "script-action" -> context.deserialize(jsonObject, ScriptAction.class);
            case "platform-action" -> context.deserialize(jsonObject, PlatformAction.class);
            default -> throw new JsonParseException("Cannot determine type");
        };
    }
}
