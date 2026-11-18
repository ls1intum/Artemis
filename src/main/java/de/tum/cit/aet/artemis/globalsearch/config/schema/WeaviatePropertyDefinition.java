package de.tum.cit.aet.artemis.globalsearch.config.schema;

/**
 * Represents a property definition in a Weaviate collection schema.
 * This record defines the structure and configuration of a property including
 * its name, data type, and indexing settings for BM25 search.
 *
 * <p>
 * See <a href="https://weaviate.io/developers/weaviate/manage-collections/inverted-index">Weaviate Inverted Index</a>
 * for detailed information on indexing properties for search and filtering.
 *
 * @param name            the name of the property
 * @param dataType        the data type of the property (e.g., "int", "text", "number")
 * @param indexSearchable whether this property should be indexed for BM25 keyword search
 * @param indexFilterable whether this property should be indexed for filtering
 * @param description     a human-readable description of the property
 */
public record WeaviatePropertyDefinition(String name, WeaviateDataType dataType, boolean indexSearchable, boolean indexFilterable, String description) {

    /**
     * Creates a non-searchable, non-filterable property.
     *
     * @param name        the property name
     * @param dataType    the data type
     * @param description the description
     * @return the property definition
     */
    public static WeaviatePropertyDefinition nonSearchable(String name, WeaviateDataType dataType, String description) {
        return new WeaviatePropertyDefinition(name, dataType, false, false, description);
    }

    /**
     * Creates a searchable property for BM25 keyword search.
     *
     * @param name        the property name
     * @param dataType    the data type
     * @param description the description
     * @return the property definition
     */
    public static WeaviatePropertyDefinition searchable(String name, WeaviateDataType dataType, String description) {
        return new WeaviatePropertyDefinition(name, dataType, true, false, description);
    }

    /**
     * Creates a filterable property.
     *
     * @param name        the property name
     * @param dataType    the data type
     * @param description the description
     * @return the property definition
     */
    public static WeaviatePropertyDefinition filterable(String name, WeaviateDataType dataType, String description) {
        return new WeaviatePropertyDefinition(name, dataType, false, true, description);
    }
}
