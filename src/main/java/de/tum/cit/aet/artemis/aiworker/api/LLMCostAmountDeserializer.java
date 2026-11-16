package de.tum.cit.aet.artemis.aiworker.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reads optional accounting metadata without rejecting the surrounding status update. */
public class LLMCostAmountDeserializer extends ValueDeserializer<Double> {

    private static final Logger log = LoggerFactory.getLogger(LLMCostAmountDeserializer.class);

    @Override
    public Double deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            double value = parser.getDoubleValue();
            if (Double.isFinite(value) && value >= 0) {
                return value;
            }
        }
        parser.skipChildren();
        log.warn("Ignoring malformed LLM USD cost metadata");
        return null;
    }
}
