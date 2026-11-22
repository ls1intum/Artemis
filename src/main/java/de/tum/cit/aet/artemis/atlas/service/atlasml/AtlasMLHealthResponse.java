package de.tum.cit.aet.artemis.atlas.service.atlasml;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The parts of the health response of the AtlasML microservice that are read when its health is reported.
 * <p>
 * The response comes from an external service, so every part is read leniently: a value of an unexpected shape is treated as absent and never makes the whole response
 * unreadable. A {@code null} component therefore means that the response did not carry a usable value there.
 *
 * @param status     the overall status, absent when it is not a scalar
 * @param components the health of the components by their names, absent when {@code components} is not an object; the health of a component that is not an object is {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
record AtlasMLHealthResponse(@Nullable @JsonDeserialize(using = AtlasMLHealthResponse.ScalarTextDeserializer.class) String status,
        @Nullable @JsonDeserialize(using = AtlasMLHealthResponse.ComponentMapDeserializer.class) Map<String, ComponentHealth> components) {

    /**
     * The health of one component of the AtlasML microservice.
     *
     * @param status      the status of the component, absent when it is not a scalar
     * @param message     the message of the component, absent when it is not a scalar
     * @param collections the health of the collections of the component by their names, absent when {@code collections} is not an object; the health of a collection that is not
     *                        an object is {@code null}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComponentHealth(@Nullable @JsonDeserialize(using = AtlasMLHealthResponse.ScalarTextDeserializer.class) String status,
            @Nullable @JsonDeserialize(using = AtlasMLHealthResponse.ScalarTextDeserializer.class) String message,
            @Nullable @JsonDeserialize(using = AtlasMLHealthResponse.CollectionMapDeserializer.class) Map<String, CollectionHealth> collections) {
    }

    /**
     * The health of one collection of a component of the AtlasML microservice.
     *
     * @param status the status of the collection, absent when it is not a scalar
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CollectionHealth(@Nullable @JsonDeserialize(using = AtlasMLHealthResponse.ScalarTextDeserializer.class) String status) {
    }

    /** Reads a JSON string, number or boolean as its text and skips any other value, which is then absent. */
    static final class ScalarTextDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken().isScalarValue()) {
                return parser.getValueAsString();
            }
            parser.skipChildren();
            return null;
        }
    }

    /**
     * Reads a JSON object as a map of the given type in the order of the object and skips any other value, which is then absent.
     * An entry whose value is not an object is kept with the value {@code null}.
     *
     * @param <T> the type of the objects in the map
     */
    abstract static class ObjectMapDeserializer<T> extends ValueDeserializer<Map<String, T>> {

        private final Class<T> valueType;

        ObjectMapDeserializer(Class<T> valueType) {
            this.valueType = valueType;
        }

        @Override
        public Map<String, T> deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren();
                return null;
            }
            Map<String, T> values = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = parser.currentName();
                if (parser.nextToken() == JsonToken.START_OBJECT) {
                    values.put(name, context.readValue(parser, valueType));
                }
                else {
                    parser.skipChildren();
                    values.put(name, null);
                }
            }
            return values;
        }
    }

    /** Reads the components of the response. */
    static final class ComponentMapDeserializer extends ObjectMapDeserializer<ComponentHealth> {

        ComponentMapDeserializer() {
            super(ComponentHealth.class);
        }
    }

    /** Reads the collections of a component. */
    static final class CollectionMapDeserializer extends ObjectMapDeserializer<CollectionHealth> {

        CollectionMapDeserializer() {
            super(CollectionHealth.class);
        }
    }
}
