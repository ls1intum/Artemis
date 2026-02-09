package de.tum.cit.aet.artemis.core.config.weaviate.schema;

import java.util.List;

/**
 * Represents a complete Weaviate collection schema definition.
 * This record defines the structure of a collection including its name,
 * properties, and vector configuration.
 *
 * @param collectionName the name of the collection in Weaviate
 * @param properties     the list of property definitions for this collection
 * @param references     the list of references to other collections (optional)
 */
public record WeaviateCollectionSchema(String collectionName, List<WeaviatePropertyDefinition> properties, List<WeaviateReferenceDefinition> references) {

    /**
     * Creates a collection schema without references.
     *
     * @param collectionName the collection name
     * @param properties     the property definitions
     * @return the collection schema
     */
    public static WeaviateCollectionSchema of(String collectionName, List<WeaviatePropertyDefinition> properties) {
        return new WeaviateCollectionSchema(collectionName, properties, List.of());
    }

    /**
     * Creates a collection schema with references.
     *
     * @param collectionName the collection name
     * @param properties     the property definitions
     * @param references     the reference definitions
     * @return the collection schema
     */
    public static WeaviateCollectionSchema of(String collectionName, List<WeaviatePropertyDefinition> properties, List<WeaviateReferenceDefinition> references) {
        return new WeaviateCollectionSchema(collectionName, properties, references);
    }

    /**
     * Gets a property definition by name.
     *
     * @param name the property name
     * @return the property definition, or null if not found
     */
    public WeaviatePropertyDefinition getProperty(String name) {
        return properties.stream().filter(property -> property.name().equals(name)).findFirst().orElse(null);
    }
}
