package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The part of JSON Schema the tools use to describe their arguments to an agent.
 *
 * @param type        {@code object}, {@code array}, {@code string}, {@code integer} or {@code boolean}
 * @param description what the value means, for the model
 * @param format      a refinement of a string, such as {@code date-time}
 * @param enumValues  the values a string may have
 * @param properties  the properties of an object
 * @param required    the names of the properties an object has to have
 * @param items       the schema of the elements of an array
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JsonSchemaDTO(String type, String description, String format, @JsonProperty("enum") List<String> enumValues, Map<String, JsonSchemaDTO> properties,
        List<String> required, JsonSchemaDTO items) {

    /**
     * A whole number.
     *
     * @param description what it means
     * @return the schema
     */
    public static JsonSchemaDTO integer(String description) {
        return new JsonSchemaDTO("integer", description, null, null, null, null, null);
    }

    /**
     * A text.
     *
     * @param description what it means
     * @return the schema
     */
    public static JsonSchemaDTO string(String description) {
        return new JsonSchemaDTO("string", description, null, null, null, null, null);
    }

    /**
     * A point in time.
     *
     * @param description what it means
     * @return the schema
     */
    public static JsonSchemaDTO dateTime(String description) {
        return new JsonSchemaDTO("string", description, "date-time", null, null, null, null);
    }

    /**
     * A flag.
     *
     * @param description what it means
     * @return the schema
     */
    public static JsonSchemaDTO bool(String description) {
        return new JsonSchemaDTO("boolean", description, null, null, null, null, null);
    }

    /**
     * A text with a fixed set of values.
     *
     * @param description what it means
     * @param values      the allowed values
     * @return the schema
     */
    public static JsonSchemaDTO choice(String description, List<String> values) {
        return new JsonSchemaDTO("string", description, null, values, null, null, null);
    }

    /**
     * A list.
     *
     * @param items the schema of an element
     * @return the schema
     */
    public static JsonSchemaDTO array(JsonSchemaDTO items) {
        return new JsonSchemaDTO("array", null, null, null, null, null, items);
    }

    /**
     * An object whose fields are the ones of a DTO of the REST API, which the agent specification describes.
     *
     * @param description which DTO it is and where to read about it
     * @return the schema
     */
    public static JsonSchemaDTO document(String description) {
        return new JsonSchemaDTO("object", description, null, null, null, null, null);
    }

    /**
     * An object with the given properties.
     *
     * @param properties the properties in the order they are described
     * @param required   the names of the properties that must be present
     * @return the schema
     */
    public static JsonSchemaDTO object(Map<String, JsonSchemaDTO> properties, List<String> required) {
        return new JsonSchemaDTO("object", null, null, null, properties, required, null);
    }
}
