package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

class HyperionExerciseMetadataParserTest {

    @ParameterizedTest
    @ValueSource(strings = { "```text\nBounded Stack\nDifficulty: easy.", "Bounded Stack\nDifficulty: easy.\n```", " \n```text\nBounded Stack\nDifficulty: easy.\n``` \n" })
    void removesOpeningAndClosingFencesIndependently(String answer) {
        var parsed = HyperionExerciseMetadataParser.parse(answer);

        assertThat(parsed.title()).isEqualTo("Bounded Stack");
        assertThat(parsed.difficulty()).isEqualTo(DifficultyLevel.EASY);
    }

    @Test
    void preservesFenceMarkersInsideTheAnswer() {
        var parsed = HyperionExerciseMetadataParser.parse("Bounded ``` Stack\nDifficulty: hard.");

        assertThat(parsed.title()).isEqualTo("Bounded ``` Stack");
        assertThat(parsed.difficulty()).isEqualTo(DifficultyLevel.HARD);
    }

    @Test
    void parsesLongFencedAnswers() {
        String title = "A".repeat(100_000);

        var parsed = HyperionExerciseMetadataParser.parse("```text\n" + title + "\nDifficulty: medium.\n```");

        assertThat(parsed.title()).isEqualTo(title);
        assertThat(parsed.difficulty()).isEqualTo(DifficultyLevel.MEDIUM);
    }
}
