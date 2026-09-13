package de.tum.cit.aet.artemis.globalsearch.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;

/**
 * The single implementation of the content hash summarizing a searchable entity's desired state in the shared
 * {@code SearchableEntities} collection.
 * <p>
 * The hash is recorded in the {@code searchable_entity_sync_state} ledger on every confirmed write, and a later
 * reconcile pass re-derives an entity and compares against it to detect drift. Both sides must produce identical
 * output for an unchanged entity, so this is a shared bean holding the one {@link ObjectMapper} rather than a
 * static helper with a mapper of its own: a second implementation that merely agrees today would, on its first
 * divergence, report every entity as drifted and rewrite the whole index continuously.
 * <p>
 * Every hash carries the {@link #CURRENT_VERSION_PREFIX}, so that deliberately changing how it is computed stays
 * distinguishable from corruption. Bump the prefix whenever the canonical form or the digest changes.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityContentHasher {

    /** Prefix stamped on every hash this class produces. */
    public static final String CURRENT_VERSION_PREFIX = "v1:";

    private final ObjectMapper objectMapper;

    public SearchableEntityContentHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Hashes an entity's Weaviate property map. The operational properties the write path adds around this value
     * (the outbox write id, and the stored copy of this hash) are deliberately not part of the map passed in, or
     * the hash would be self-referential and would change on every write.
     *
     * @param properties the property map as produced by a {@code SearchableEntityDTO.toPropertyMap()}
     * @return the versioned hash, for example {@code v1:9f86d081...}
     */
    public String hash(Map<String, Object> properties) {
        return CURRENT_VERSION_PREFIX + sha256Hex(canonicalJson(properties));
    }

    /**
     * Returns whether a stored hash was produced by the algorithm this class currently implements. A hash from an
     * older version is not corruption, so a reconcile pass has to tell the two apart before deciding to repair.
     *
     * @param contentHash a hash previously stored in the ledger or on an indexed row, possibly null
     * @return true if the hash carries the current version prefix
     */
    public static boolean isCurrentVersion(String contentHash) {
        return contentHash != null && contentHash.startsWith(CURRENT_VERSION_PREFIX);
    }

    /**
     * Renders the property map as canonical JSON so equal maps hash equal. Sorting the keys is what makes it
     * canonical, since {@code toPropertyMap()} builds a {@link java.util.HashMap} with no guaranteed order.
     * Sorting the top level suffices because the maps are flat: every value is JSON-native, never a nested map.
     */
    private String canonicalJson(Map<String, Object> properties) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(properties));
        }
        catch (JsonProcessingException e) {
            throw new WeaviateException("Failed to serialize a searchable entity property map for hashing: " + e.getMessage(), e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
