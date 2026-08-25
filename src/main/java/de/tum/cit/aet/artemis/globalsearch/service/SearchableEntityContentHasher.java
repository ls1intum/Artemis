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
 * The single implementation of the content hash that summarizes a searchable entity's desired state in the
 * shared {@code SearchableEntities} Weaviate collection.
 * <p>
 * The hash is recorded in the {@code searchable_entity_sync_state} ledger on every confirmed write, and a
 * later reconcile pass re-derives an entity and compares its hash against the ledger to detect drift. Both
 * sides MUST produce byte-identical output for an unchanged entity. A second, independently written
 * implementation that merely happens to agree today would, on the first divergence, report every entity as
 * drifted and rewrite the entire index continuously. That failure is silent and expensive, which is why this
 * is a shared bean holding the one {@link ObjectMapper} rather than a static helper with a mapper of its own.
 * <p>
 * <b>Versioning.</b> Every hash carries the {@link #CURRENT_VERSION_PREFIX}. Without it, deliberately changing
 * how the hash is computed would be indistinguishable from data corruption: every stored hash would stop
 * matching at once and the reconciler would treat a one-line code change as a corpus-wide repair. With it, a
 * hash produced by an older algorithm is recognizable as such, so it can be accepted, migrated, or re-derived
 * on purpose. Bump the prefix whenever the canonical form or the digest changes.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityContentHasher {

    /**
     * Prefix stamped on every hash this class produces. Bump it when the canonical form or the digest changes.
     */
    public static final String CURRENT_VERSION_PREFIX = "v1:";

    private final ObjectMapper objectMapper;

    public SearchableEntityContentHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Hashes an entity's Weaviate property map.
     * <p>
     * Operational properties that the write path adds around this value (the outbox write id, and the stored
     * copy of this very hash) are deliberately NOT part of the map passed in: including them would make the
     * hash either self-referential or different on every write, and the ledger would flap instead of detecting
     * real content drift.
     *
     * @param properties the property map as produced by a {@code SearchableEntityDTO.toPropertyMap()}
     * @return the versioned hash, for example {@code v1:9f86d081...}
     */
    public String hash(Map<String, Object> properties) {
        return CURRENT_VERSION_PREFIX + sha256Hex(canonicalJson(properties));
    }

    /**
     * Whether a stored hash was produced by the algorithm this class currently implements. A hash from an older
     * version is not corruption, so a reconcile pass must be able to tell the two apart before deciding to repair.
     *
     * @param contentHash a hash previously stored in the ledger or on an indexed row, possibly {@code null}
     * @return {@code true} if the hash carries the current version prefix
     */
    public static boolean isCurrentVersion(String contentHash) {
        return contentHash != null && contentHash.startsWith(CURRENT_VERSION_PREFIX);
    }

    /**
     * Renders the property map as canonical JSON so equal maps hash equal.
     * <p>
     * Sorting the keys is what makes the rendering canonical, since {@code toPropertyMap()} builds a
     * {@link java.util.HashMap} whose iteration order is not guaranteed. Sorting the top level is sufficient
     * because the maps are flat by construction: every value is JSON-native (string, number or boolean, with
     * dates already formatted as RFC3339 strings), never a nested map whose own key order could vary.
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
