package de.tum.cit.aet.artemis.core.config.weaviate.schema;

/**
 * Represents a reference from one Weaviate collection to another.
 * References allow creating relationships between objects in different collections.
 *
 * @param name             the name of the reference property
 * @param targetCollection the name of the collection being referenced
 * @param description      a human-readable description of the reference
 */
public record WeaviateReferenceDefinition(String name, String targetCollection, String description) {

    /**
     * Creates a reference definition.
     *
     * @param name             the reference name
     * @param targetCollection the target collection name
     * @param description      the description
     * @return the reference definition
     */
    public static WeaviateReferenceDefinition of(String name, String targetCollection, String description) {
        return new WeaviateReferenceDefinition(name, targetCollection, description);
    }
}
