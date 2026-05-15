package de.tum.cit.aet.artemis.globalsearch.service.migration;

import java.io.IOException;

import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Represents a single versioned migration step for the Weaviate schema.
 * <p>
 * Migrations are applied sequentially by {@link de.tum.cit.aet.artemis.globalsearch.service.WeaviateMigrationService}
 * during application startup. Each migration transforms the Weaviate state from
 * version {@code N-1} to version {@code N}.
 * <p>
 * Implementations must be idempotent: if a migration is interrupted and re-run,
 * it must not produce duplicate data or errors.
 */
public interface WeaviateMigration {

    /**
     * The target schema version after this migration completes.
     * Migrations are ordered and executed by ascending version number.
     *
     * @return the target version (e.g. 1 for the v0 → v1 migration)
     */
    int targetVersion();

    /**
     * Human-readable description of what this migration does, used for logging.
     *
     * @return the migration description
     */
    String description();

    /**
     * Executes the migration.
     * <p>
     * The caller guarantees that:
     * <ul>
     * <li>All target collections already exist (created by
     * {@link de.tum.cit.aet.artemis.globalsearch.service.WeaviateService#initializeCollections()})</li>
     * <li>No migration with a higher version has been applied yet</li>
     * </ul>
     *
     * @param client           the Weaviate client
     * @param collectionPrefix the collection name prefix (e.g. "Artemis_" or "" for tests)
     * @throws IOException if a Weaviate API call fails
     */
    void migrate(WeaviateClient client, String collectionPrefix) throws IOException;
}
