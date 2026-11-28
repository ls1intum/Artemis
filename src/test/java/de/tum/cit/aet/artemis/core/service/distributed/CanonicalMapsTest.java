package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.CanonicalMaps;

class CanonicalMapsTest {

    @Test
    void rejectsDistinctKeysWithTheSameNaturalOrder() {
        Map<BigDecimal, String> source = Map.of(new BigDecimal("1.0"), "first", new BigDecimal("1.00"), "second");

        assertThatIllegalArgumentException().isThrownBy(() -> CanonicalMaps.copyOf(source)).withMessageContaining("natural ordering");
        assertThat(source).hasSize(2);
    }

    @Test
    void preservesEntriesInAnIndependentUnmodifiableSortedCopy() {
        Map<Long, String> source = new LinkedHashMap<>();
        source.put(2L, "second");
        source.put(1L, "first");

        Map<Long, String> copy = CanonicalMaps.copyOf(source);
        source.put(3L, "third");

        assertThat(copy.keySet()).containsExactly(1L, 2L);
        assertThat(copy).containsEntry(1L, "first").containsEntry(2L, "second").hasSize(2);
        assertThatThrownBy(() -> copy.put(3L, "third")).isInstanceOf(UnsupportedOperationException.class);
    }
}
