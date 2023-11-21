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

        if (jsonObject.has("script")) {
            return context.deserialize(jsonObject, ScriptAction.class);
        }
        else if (jsonObject.has("platform")) {
            return context.deserialize(jsonObject, PlatformAction.class);
        }

        throw new JsonParseException("Cannot determine type");
    }
}
