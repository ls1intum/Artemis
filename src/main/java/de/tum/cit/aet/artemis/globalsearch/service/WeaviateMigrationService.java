package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.service.migration.V0ToV1Migration;
import de.tum.cit.aet.artemis.globalsearch.service.migration.WeaviateMigration;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Manages Weaviate schema versioning and runs pending migrations at startup.
 * <p>
 * Schema version is tracked in a dedicated {@code SchemaVersion} Weaviate collection
 * with a single object holding the current version number. Migrations are numbered
 * sequentially (v0 → v1, v1 → v2, …) and applied in order.
 * <p>
 * The service distinguishes three startup scenarios:
 * <ol>
 * <li><b>Fresh install</b> — no version collection and no legacy collections exist →
 * version is set to {@link #LATEST_VERSION}, no migrations run.</li>
 * <li><b>Upgrade from v0</b> — no version collection but the legacy {@code Exercises}
 * collection exists → version starts at 0, all migrations run.</li>
 * <li><b>Upgrade from vN</b> — version collection exists → stored version is read,
 * only migrations with {@code targetVersion > storedVersion} run.</li>
 * </ol>
 * <p>
 * Called by {@link WeaviateService#initializeCollections()} after all data collections
 * have been created so migration code can safely write to the target collections.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class WeaviateMigrationService {

    private static final Logger log = LoggerFactory.getLogger(WeaviateMigrationService.class);

    /**
     * Base name of the version-tracking collection (the configured collection prefix is
     * prepended at runtime, e.g. {@code Artemis_SchemaVersion}).
     */
    static final String VERSION_COLLECTION_BASE_NAME = "SchemaVersion";

    /**
     * Property name inside the version-tracking collection.
     */
    static final String SCHEMA_VERSION_PROPERTY = "schema_version";

    /**
     * Fixed UUID for the single version object so it can be addressed directly.
     */
    private static final String VERSION_OBJECT_UUID = "00000000-0000-0000-0000-000000000001";

    /**
     * All registered migrations in ascending target-version order.
     * To add a new migration, append it to this list.
     */
    static final List<WeaviateMigration> MIGRATIONS = List.of(new V0ToV1Migration());

    /**
     * The schema version that corresponds to the current codebase.
     */
    static final int LATEST_VERSION = MIGRATIONS.getLast().targetVersion();

    private final WeaviateClient client;

    private final String collectionPrefix;

    public WeaviateMigrationService(WeaviateClient client, WeaviateConfigurationProperties properties) {
        this.client = client;
        this.collectionPrefix = properties.collectionPrefix();
    }

    /**
     * Runs all pending migrations. Must be called after all data collections have been
     * created by {@link WeaviateService#initializeCollections()}.
     *
     * @throws WeaviateException if any migration fails (non-recoverable)
     */
    public void runPendingMigrations() {
        try {
            ensureVersionCollectionExists();
            int currentVersion = detectCurrentVersion();

            if (currentVersion >= LATEST_VERSION) {
                log.debug("Weaviate schema is up-to-date (version {})", currentVersion);
                return;
            }

            int pendingCount = (int) MIGRATIONS.stream().filter(m -> m.targetVersion() > currentVersion).count();
            log.info("Weaviate schema version is {}, latest is {}. Running {} pending migration(s)...", currentVersion, LATEST_VERSION, pendingCount);

            for (WeaviateMigration migration : MIGRATIONS) {
                if (migration.targetVersion() <= currentVersion) {
                    continue;
                }
                log.info("Running migration v{} → v{}: {}", migration.targetVersion() - 1, migration.targetVersion(), migration.description());
                migration.migrate(client, collectionPrefix);
                storeVersion(migration.targetVersion());
                log.info("Migration v{} → v{} completed successfully", migration.targetVersion() - 1, migration.targetVersion());
            }

            log.info("All Weaviate migrations completed. Schema is now at version {}", LATEST_VERSION);
        }
        catch (Exception e) {
            log.error("Weaviate migration failed: {}. Search may return incomplete results until entities are re-indexed.", e.getMessage(), e);
            throw new WeaviateException("Weaviate migration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Detects the current schema version.
     * <ul>
     * <li>If the version collection has an object → return the stored version.</li>
     * <li>If empty and a legacy v0 collection exists → return 0 (needs migration).</li>
     * <li>If empty and no legacy collections → fresh install, store and return {@link #LATEST_VERSION}.</li>
     * </ul>
     */
    private int detectCurrentVersion() throws IOException {
        var versionCollection = client.collections.use(collectionPrefix + VERSION_COLLECTION_BASE_NAME);

        var result = versionCollection.query.fetchObjects(b -> b.limit(1));
        if (!result.objects().isEmpty()) {
            Object raw = result.objects().getFirst().properties().get(SCHEMA_VERSION_PROPERTY);
            if (raw instanceof Number number) {
                return number.intValue();
            }
        }

        // Version collection is empty — determine if this is a fresh install or an upgrade
        if (isLegacyV0Deployment()) {
            log.info("Detected legacy v0 deployment ('{}' collection exists)", collectionPrefix + V0ToV1Migration.LEGACY_EXERCISES_COLLECTION);
            storeVersion(0);
            return 0;
        }

        log.info("Fresh Weaviate installation detected, setting schema version to {}", LATEST_VERSION);
        storeVersion(LATEST_VERSION);
        return LATEST_VERSION;
    }

    /**
     * Checks whether the legacy v0 {@code Exercises} collection exists.
     */
    private boolean isLegacyV0Deployment() throws IOException {
        return client.collections.exists(collectionPrefix + V0ToV1Migration.LEGACY_EXERCISES_COLLECTION);
    }

    /**
     * Creates the version-tracking collection if it does not already exist.
     * The collection has no vectorizer (metadata only) and a single integer property.
     */
    private void ensureVersionCollectionExists() throws IOException {
        String name = collectionPrefix + VERSION_COLLECTION_BASE_NAME;
        if (client.collections.exists(name)) {
            return;
        }
        client.collections.create(name, c -> {
            c.vectorConfig(VectorConfig.selfProvided());
            c.properties(Property.integer(SCHEMA_VERSION_PROPERTY));
            return c;
        });
        log.info("Created schema version tracking collection '{}'", name);
    }

    /**
     * Writes the given version number to the version-tracking collection,
     * replacing any existing version object.
     */
    private void storeVersion(int version) throws IOException {
        var versionCollection = client.collections.use(collectionPrefix + VERSION_COLLECTION_BASE_NAME);
        Map<String, Object> props = Map.of(SCHEMA_VERSION_PROPERTY, version);

        if (versionCollection.data.exists(VERSION_OBJECT_UUID)) {
            versionCollection.data.replace(VERSION_OBJECT_UUID, r -> r.properties(props));
        }
        else {
            versionCollection.data.insert(props, o -> o.uuid(VERSION_OBJECT_UUID));
        }
    }
}
