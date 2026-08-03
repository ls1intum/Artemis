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

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;

/**
 * Computes a deterministic SHA-256 content hash over the property map that a SearchableEntity DTO writes to
 * Weaviate (its {@code toPropertyMap()}), so any change to a searchable or filterable field flips the hash.
 * <p>
 * The hash is the fingerprint of the desired row content, computed from the database (the source of truth); the
 * census and the durable drain compare it against what {@code SearchableEntities} actually holds to detect
 * staleness. It mirrors the length-prefixed field encoding of {@code IrisLectureUnitSyncService} but runs over a
 * generic, key-sorted property map, so the result is independent of the map's iteration order.
 * <p>
 * Pure function: no Weaviate and no database access.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityHashService {

    private static final String SHA_256 = "SHA-256";

    /**
     * Computes the deterministic content hash of a SearchableEntity property map.
     * <p>
     * Keys are sorted so equal content always yields an equal hash regardless of the map's iteration order, and
     * every field is length-prefixed so no concatenation of neighbouring fields can collide. The type is folded in
     * explicitly so two rows with otherwise identical maps but different types never share a hash, even if a caller
     * passes a map without the type discriminator.
     *
     * @param type        the entity type discriminator (see {@code SearchableEntitySchema.TypeValues})
     * @param propertyMap the exact property map the DTO writes to Weaviate
     * @return a stable 64-character lowercase hex SHA-256 hash
     */
    public String contentHash(String type, Map<String, Object> propertyMap) {
        MessageDigest digest = createMessageDigest();
        appendField(digest, "entityType", type);
        new TreeMap<>(propertyMap).forEach((key, value) -> appendField(digest, key, value));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not initialize SHA-256 digest", e);
        }
    }

    private static void appendField(MessageDigest digest, String label, Object value) {
        digest.update(label.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        if (value == null) {
            digest.update("-1".getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update((byte) '\n');
            return;
        }
        String stringValue = value.toString();
        digest.update(Integer.toString(stringValue.length()).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(stringValue.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }
}
