package de.tum.cit.aet.artemis.globalsearch.service.migration;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Migrates from schema v1 to v2 by dropping and recreating the {@code SearchableEntities} collection.
 * <p>
 * Schema v1 created the {@code type} property with Weaviate's default {@code "word"} tokenization,
 * which splits values on underscores. This caused {@code type Equal "lecture"} to also match
 * {@code type="lecture_unit"} objects (tokenized to ["lecture","unit"]), and {@code type Equal "post"}
 * to also match {@code type="answer_post"} objects. Because the lecture disjunct in the compound OR
 * search filter has no release-date constraint, this allowed students to see unreleased lecture units.
 * <p>
 * Schema v2 uses {@code "field"} tokenization for filterable-only text properties, giving exact-match
 * semantics for {@code Equal} filters and eliminating the collision.
 * <p>
 * The migration simply deletes the collection. {@link de.tum.cit.aet.artemis.globalsearch.service.WeaviateMigrationStartupService}
 * calls {@code weaviateService.ensureAllCollectionsExist()} immediately after migrations complete,
 * which recreates the collection with the new schema. All searchable entities are then re-indexed
 * on their next write (courses, exercises, lecture units, etc. push updates on every save).
 * <p>
 * If the collection does not exist (already deleted or a fresh install that skipped to v2), this
 * migration is a no-op.
 */
public class V1ToV2Migration implements WeaviateMigration {

    private static final Logger log = LoggerFactory.getLogger(V1ToV2Migration.class);

    @Override
    public int targetVersion() {
        return 2;
    }

    @Override
    public String description() {
        return "Drop SearchableEntities collection to force recreation with field tokenization on the type property (fixes lecture/lecture_unit and post/answer_post filter collisions)";
    }

    @Override
    public void migrate(WeaviateClient client, String collectionPrefix) throws IOException {
        String collectionName = collectionPrefix + SearchableEntitySchema.COLLECTION_NAME;

        if (!client.collections.exists(collectionName)) {
            log.info("V1→V2: Collection '{}' not found — nothing to drop", collectionName);
            return;
        }

        log.info("V1→V2: Dropping '{}' to force recreation with field tokenization...", collectionName);
        client.collections.delete(collectionName);
        log.info("V1→V2: Dropped '{}'. WeaviateMigrationStartupService will recreate it; entities will be re-indexed on next write.", collectionName);
    }
}
