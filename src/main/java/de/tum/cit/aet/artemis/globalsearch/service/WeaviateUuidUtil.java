package de.tum.cit.aet.artemis.globalsearch.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Single source of truth for deterministic Weaviate object UUIDs.
 * <p>
 * Every component that needs to map an Artemis {@code (type, entityId)} pair to a
 * Weaviate object UUID must use {@link #deterministicUuid(String, Long)} so that
 * normal upserts and schema migrations always produce the same UUID for the same
 * entity.
 */
public final class WeaviateUuidUtil {

    /**
     * Fixed namespace prefix included in the string passed to
     * {@link UUID#nameUUIDFromBytes(byte[])} to derive deterministic UUID v3 (MD5-based)
     * identifiers for Weaviate objects. The full input is {@code "namespace:type:entityId"},
     * ensuring the same entity always maps to the same Weaviate object UUID across nodes.
     */
    static final UUID WEAVIATE_UUID_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private WeaviateUuidUtil() {
        // utility class
    }

    /**
     * Derives a deterministic UUID v3 (MD5-based) from {@code (type, entityId)} via
     * {@link UUID#nameUUIDFromBytes(byte[])} so the same entity always maps to the same
     * Weaviate object UUID regardless of which node performs the upsert. The type is
     * included because entity IDs are only unique within a table (e.g. exercise 42 and
     * FAQ 42 can coexist).
     */
    public static String deterministicUuid(String type, Long entityId) {
        return UUID.nameUUIDFromBytes((WEAVIATE_UUID_NAMESPACE + ":" + type + ":" + entityId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
