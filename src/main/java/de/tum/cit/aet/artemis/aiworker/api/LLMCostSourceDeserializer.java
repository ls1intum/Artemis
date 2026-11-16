package de.tum.cit.aet.artemis.aiworker.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reads known accounting sources, ignoring unusable optional metadata. */
public class LLMCostSourceDeserializer extends ValueDeserializer<String> {

    private static final Logger log = LoggerFactory.getLogger(LLMCostSourceDeserializer.class);

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String source = parser.getString();
            if ("PROVIDER".equals(source) || "CONFIGURATION".equals(source)) {
                return source;
            }
        }
        parser.skipChildren();
        log.warn("Ignoring unknown LLM cost source metadata");
        return null;
    }
}
