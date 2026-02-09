package de.tum.cit.aet.artemis.core.config.weaviate.schema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.entitySchemas.ProgrammingExerciseSchema;

/**
 * Registry of all Weaviate collection schemas defined in Artemis.
 * <p>
 * Individual schema definitions live in their own classes (e.g. {@link ProgrammingExerciseSchema}).
 * This class aggregates them for startup initialization and lookup.
 */
public final class WeaviateSchemas {

    /**
     * Utility class, should not be instantiated.
     */
    private WeaviateSchemas() {
    }

    /**
     * List of all Weaviate collection schemas defined in Artemis.
     */
    public static final List<WeaviateCollectionSchema> ALL_SCHEMAS = List.of(ProgrammingExerciseSchema.SCHEMA);

    /**
     * Map of collection name to schema for quick lookup.
     */
    public static final Map<String, WeaviateCollectionSchema> SCHEMAS_BY_NAME = ALL_SCHEMAS.stream()
            .collect(Collectors.toMap(WeaviateCollectionSchema::collectionName, Function.identity()));

    /**
     * Gets a schema by collection name.
     *
     * @param collectionName the collection name
     * @return the schema, or null if not found
     */
    public static WeaviateCollectionSchema getSchema(String collectionName) {
        return SCHEMAS_BY_NAME.get(collectionName);
    }
}
