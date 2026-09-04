package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SeededStructuralTestsTest {

    @Test
    void rejectsNameOnlyAuthorityAndNonCanonicalArchivePaths() {
        assertThatThrownBy(() -> new SeededStructuralTests(Set.of("testClass[X]"), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeededStructuralTests(Set.of("testClass[X]"), Map.of("../candidate/Test.java", "class Test {}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeededStructuralTests(Set.of("testClass[X]"), Map.of("test/./candidate/Test.java", "class Test {}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeededStructuralTests(Set.of("testClass[X]"), Map.of("test/candidate/Test.java\ninjected", "class Test {}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
