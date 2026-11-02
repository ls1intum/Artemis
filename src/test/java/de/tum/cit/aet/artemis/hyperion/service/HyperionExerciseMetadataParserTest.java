package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

class HyperionExerciseMetadataParserTest {

    @ParameterizedTest
    @ValueSource(strings = { "{\"title\":\"Bounded Stack\",\"difficulty\":\"HARD\"}", "```json\n{\"title\":\"Bounded Stack\",\"difficulty\":\"HARD\"}\n```",
            "```\n{\"title\":\"Bounded Stack\",\"difficulty\":\"HARD\"}\n```", "```json\n{\"title\":\"Bounded Stack\",\"difficulty\":\"HARD\"}",
            "{\"title\":\"Bounded Stack\",\"difficulty\":\"HARD\"}\n```" })
    void preservesMetadataWithEitherOrBothOuterFences(String answer) {
        assertThat(HyperionExerciseMetadataParser.parse(answer)).isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer("Bounded Stack", DifficultyLevel.HARD));
    }

    @Test
    void preservesFencesInsideTheTitle() {
        String title = "Array ``` Examples";

        assertThat(HyperionExerciseMetadataParser.parse(title + "\nDifficulty: easy.")).isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer(title, DifficultyLevel.EASY));
    }

    @Test
    void stripsOuterFencesFromProseWithoutChangingTheFirstLine() {
        assertThat(HyperionExerciseMetadataParser.parse("```text\nBounded Stack\nDifficulty: medium.\n```"))
                .isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer("Bounded Stack", DifficultyLevel.MEDIUM));
    }

    @ParameterizedTest
    @ValueSource(strings = { "```text\nBounded Stack", "Bounded Stack```", "\t```text\r\nBounded Stack\r\n```\t" })
    void removesEitherOuterFenceFromPlainText(String answer) {
        assertThat(HyperionExerciseMetadataParser.parse(answer)).isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer("Bounded Stack", null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "```", "```text\n```", " \t```\r\n```\t " })
    void acceptsEmptyFencedAnswers(String answer) {
        assertThat(HyperionExerciseMetadataParser.parse(answer)).isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer("", null));
    }

    @Test
    void preservesLongFencedModelAnswers() {
        String title = "a".repeat(100_000);
        String answer = "```json\n{\"title\":\"" + title + "\",\"difficulty\":\"EASY\"}\n```";

        assertThat(HyperionExerciseMetadataParser.parse(answer)).isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer(title, DifficultyLevel.EASY));
    }

    @Test
    void preservesMetadataAcrossLongInteriorWhitespace() {
        String answer = "```json\n{\"title\":\"Bounded Stack\"," + " ".repeat(100_000) + "\"difficulty\":\"HARD\"}\n```";

        assertThat(HyperionExerciseMetadataParser.parse(answer)).isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer("Bounded Stack", DifficultyLevel.HARD));
    }

    @ParameterizedTest
    @CsvSource({ "Difficulty: easy,EASY", "an easy difficulty,EASY", "Difficulty level = hard,HARD", "Difficulty MEDIUM,MEDIUM", "difficultylevel-hard,HARD" })
    void readsExplicitDifficultyWithOptionalLevelAndSeparator(String brief, DifficultyLevel expected) {
        assertThat(HyperionExerciseMetadataParser.difficultyStatedInBrief(brief)).isEqualTo(expected);
    }

    @Test
    void readsDifficultyAcrossTabsAndLineBreaks() {
        assertThat(HyperionExerciseMetadataParser.difficultyStatedInBrief("DIFFICULTY\tLEVEL\r\n:\tHARD")).isEqualTo(DifficultyLevel.HARD);
    }

    @Test
    void readsDifficultyAcrossLongWhitespaceWithoutBacktracking() {
        String whitespace = " ".repeat(100_000);

        assertThat(HyperionExerciseMetadataParser.difficultyStatedInBrief("difficulty" + whitespace + "hard")).isEqualTo(DifficultyLevel.HARD);
        assertThat(HyperionExerciseMetadataParser.difficultyStatedInBrief("easy" + whitespace + "difficulty")).isEqualTo(DifficultyLevel.EASY);
        assertThat(HyperionExerciseMetadataParser.difficultyStatedInBrief("difficulty" + whitespace + "invalid")).isNull();
        assertThat(HyperionExerciseMetadataParser.difficultyStatedInBrief("difficulty" + whitespace + "level" + whitespace + ":" + whitespace + "invalid")).isNull();
    }

    @Test
    void preservesLongFencedProseAnswers() {
        String title = "A".repeat(100_000);

        assertThat(HyperionExerciseMetadataParser.parse("```text\n" + title + "\nDifficulty: medium.\n```"))
                .isEqualTo(new HyperionExerciseMetadataParser.ModelAnswer(title, DifficultyLevel.MEDIUM));
    }

}
