package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationToolchainTest {

    @ParameterizedTest
    @ValueSource(strings = { "a", "java-gradle", "python3-pytest-8", "a0-b1-c2" })
    void acceptsLowercaseAlphanumericSegments(String id) {
        assertThat(new GenerationToolchain(id).id()).isEqualTo(id);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "1java", "Java-gradle", "-java", "java-", "java--gradle", "java_gradle", "java gradle", "java\ngradle" })
    void rejectsMalformedIdentifiers(String id) {
        assertThatThrownBy(() -> new GenerationToolchain(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsIdentifiersAtTheLengthLimit() {
        String id = "ab" + "-c".repeat(31);

        assertThat(new GenerationToolchain(id).id()).isEqualTo(id);
    }

    @Test
    void rejectsIdentifiersBeyondTheLengthLimit() {
        assertThatThrownBy(() -> new GenerationToolchain("a".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationToolchain("a" + "-b".repeat(100_000))).isInstanceOf(IllegalArgumentException.class);
    }
}
