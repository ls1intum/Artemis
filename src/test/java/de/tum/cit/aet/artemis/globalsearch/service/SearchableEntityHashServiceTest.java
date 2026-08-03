package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SearchableEntityHashServiceTest {

    private final SearchableEntityHashService hashService = new SearchableEntityHashService();

    @Test
    void returnsStable64CharHexForTheSameContent() {
        Map<String, Object> properties = Map.of("type", "faq", "entity_id", 42L, "title", "How is the exam graded?");

        String first = hashService.contentHash("faq", properties);
        String second = hashService.contentHash("faq", new HashMap<>(properties));

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void isIndependentOfKeyOrder() {
        Map<String, Object> ascending = new LinkedHashMap<>();
        ascending.put("course_id", 7L);
        ascending.put("entity_id", 42L);
        ascending.put("title", "Normalization");

        Map<String, Object> shuffled = new LinkedHashMap<>();
        shuffled.put("title", "Normalization");
        shuffled.put("entity_id", 42L);
        shuffled.put("course_id", 7L);

        assertThat(hashService.contentHash("lecture_unit", ascending)).isEqualTo(hashService.contentHash("lecture_unit", shuffled));
    }

    @Test
    void changesWhenAFieldValueChanges() {
        Map<String, Object> original = Map.of("entity_id", 42L, "title", "Transactions");
        Map<String, Object> edited = Map.of("entity_id", 42L, "title", "Transactions and Recovery");

        assertThat(hashService.contentHash("lecture_unit", original)).isNotEqualTo(hashService.contentHash("lecture_unit", edited));
    }

    @Test
    void changesWhenTheTypeChanges() {
        Map<String, Object> properties = Map.of("entity_id", 42L, "title", "Overview");

        assertThat(hashService.contentHash("faq", properties)).isNotEqualTo(hashService.contentHash("channel", properties));
    }

    @Test
    void distinguishesFieldBoundariesFromConcatenation() {
        // Without length-prefixing, {a:"xy", b:"z"} and {a:"x", b:"yz"} would hash the same; they must not.
        Map<String, Object> left = Map.of("a", "xy", "b", "z");
        Map<String, Object> right = Map.of("a", "x", "b", "yz");

        assertThat(hashService.contentHash("faq", left)).isNotEqualTo(hashService.contentHash("faq", right));
    }

    @Test
    void treatsNullValueDifferentlyFromEmptyString() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("description", null);
        Map<String, Object> withEmpty = new HashMap<>();
        withEmpty.put("description", "");

        assertThat(hashService.contentHash("faq", withNull)).isNotEqualTo(hashService.contentHash("faq", withEmpty));
    }
}
