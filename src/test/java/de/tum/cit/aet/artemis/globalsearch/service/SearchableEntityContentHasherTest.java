package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class SearchableEntityContentHasherTest {

    private final SearchableEntityContentHasher contentHasher = new SearchableEntityContentHasher(JsonObjectMapper.get());

    @Test
    void testHash_isStableAcrossKeyInsertionOrder() {
        // toPropertyMap builds a HashMap, so iteration order is not guaranteed. Two maps holding the same
        // entries must hash equal, or a reconcile pass would report drift on entities that never changed.
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("type", "course");
        first.put("entity_id", 42L);
        first.put("title", "Algorithms");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("title", "Algorithms");
        second.put("entity_id", 42L);
        second.put("type", "course");

        assertThat(contentHasher.hash(first)).isEqualTo(contentHasher.hash(second));
    }

    @Test
    void testHash_differsWhenAValueChanges() {
        Map<String, Object> properties = new HashMap<>(Map.of("type", "course", "entity_id", 42L, "title", "Algorithms"));
        String before = contentHasher.hash(properties);

        properties.put("title", "Advanced Algorithms");

        assertThat(contentHasher.hash(properties)).isNotEqualTo(before);
    }

    @Test
    void testHash_differsWhenAnOptionalPropertyAppears() {
        // toPropertyMap omits null optional fields, so gaining a value must change the hash. This is what makes
        // a newly added DTO field surface as drift on entities that carry it.
        Map<String, Object> withoutDescription = new HashMap<>(Map.of("type", "course", "entity_id", 42L));
        Map<String, Object> withDescription = new HashMap<>(withoutDescription);
        withDescription.put("description", "A course description");

        assertThat(contentHasher.hash(withDescription)).isNotEqualTo(contentHasher.hash(withoutDescription));
    }

    @Test
    void testHash_carriesTheCurrentVersionPrefix() {
        String hash = contentHasher.hash(Map.of("type", "course", "entity_id", 42L));

        assertThat(hash).startsWith(SearchableEntityContentHasher.CURRENT_VERSION_PREFIX)
                // the prefix plus a full SHA-256 hex digest
                .hasSize(SearchableEntityContentHasher.CURRENT_VERSION_PREFIX.length() + 64);
        assertThat(SearchableEntityContentHasher.isCurrentVersion(hash)).isTrue();
    }

    @Test
    void testIsCurrentVersion_rejectsAnOlderOrAbsentVersion() {
        // A hash from an older algorithm is not corruption. A reconcile pass has to tell the two apart before
        // deciding to repair, so an unprefixed or differently prefixed hash must not read as current.
        assertThat(SearchableEntityContentHasher.isCurrentVersion("a".repeat(64))).isFalse();
        assertThat(SearchableEntityContentHasher.isCurrentVersion("v0:" + "a".repeat(64))).isFalse();
        assertThat(SearchableEntityContentHasher.isCurrentVersion(null)).isFalse();
    }
}
