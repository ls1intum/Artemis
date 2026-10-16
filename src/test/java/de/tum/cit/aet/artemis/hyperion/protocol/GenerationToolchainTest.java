package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationToolchainTest {

    @ParameterizedTest
    @ValueSource(strings = { "a", "java-gradle", "python3-pytest8", "a-b-c" })
    void preservesValidIdentifiers(String id) {
        assertThat(new GenerationToolchain(id).id()).isEqualTo(id);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "Java-gradle", "3java-gradle", "-java", "java-", "java--gradle", "java_gradle", "java.gradle", "java gradle", "java\ngradle" })
    void rejectsInvalidIdentifiers(String id) {
        assertThatThrownBy(() -> new GenerationToolchain(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsMaximumLengthWithManySegments() {
        String id = "a-".repeat(31) + "bc";

        assertThat(id).hasSize(64);
        assertThat(new GenerationToolchain(id).id()).isEqualTo(id);
        assertThatThrownBy(() -> new GenerationToolchain(id + "d")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationToolchain("a-".repeat(31) + "b-")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsVeryLongInputsWithoutOverflowingTheStack() {
        assertThatThrownBy(() -> new GenerationToolchain("a-".repeat(100_000) + "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationToolchain("a".repeat(200_000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patternMatchesManySegmentsWithoutRecursiveBacktracking() throws ReflectiveOperationException {
        var field = GenerationToolchain.class.getDeclaredField("ID_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        String id = "a-".repeat(100_000) + "a";

        assertThat(pattern.matcher(id).matches()).isTrue();
        assertThat(pattern.matcher(id + "-").matches()).isFalse();
    }
}
