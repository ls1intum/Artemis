package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.TITLE_NAME_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The sanitizer's contract is that Artemis accepts whatever it returns, so most of these assert the result against the production title rules rather than against a literal.
 */
class HyperionExerciseTitleSanitizerTest {

    @Test
    void keepsATitleThatIsAlreadyValid() {
        assertThat(HyperionExerciseTitleSanitizer.sanitize("Bounded Stack")).isEqualTo("Bounded Stack");
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"Bounded Stack\"", "Title: Bounded Stack", "# Bounded Stack", "Bounded Stack (Generics)", "**Bounded Stack**", "Bounded Stack.",
            "  Bounded\tStack  ", "Bounded\u00A0Stack" })
    void stripsEverythingArtemisWouldRejectAndKeepsTheWords(String answer) {
        String sanitized = HyperionExerciseTitleSanitizer.sanitize(answer);

        assertThat(TITLE_NAME_PATTERN.matcher(sanitized).matches()).isTrue();
        assertThat(sanitized).contains("Bounded Stack");
    }

    @Test
    void keepsNonAsciiLettersBecauseTheTitleRulesAreUnicodeAware() {
        String sanitized = HyperionExerciseTitleSanitizer.sanitize("Bäume und Grüße");

        assertThat(sanitized).isEqualTo("Bäume und Grüße");
        assertThat(TITLE_NAME_PATTERN.matcher(sanitized).matches()).isTrue();
    }

    @Test
    void takesOnlyTheFirstLineWhenTheModelExplainsItself() {
        assertThat(HyperionExerciseTitleSanitizer.sanitize("\nBounded Stack\n\nI chose this because it names the data structure.")).isEqualTo("Bounded Stack");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "!!!", "ab", ".", "\n\n" })
    void yieldsTheEmptyStringWhenNothingUsableSurvives(String answer) {
        assertThat(HyperionExerciseTitleSanitizer.sanitize(answer)).isEmpty();
    }

    @Test
    void yieldsTheEmptyStringForNoAnswerAtAll() {
        assertThat(HyperionExerciseTitleSanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void capsALongAnswerAtAWordBoundary() {
        String sanitized = HyperionExerciseTitleSanitizer.sanitize("Implementing a thread safe bounded stack with generics and a dedicated empty stack exception");

        assertThat(sanitized).hasSizeLessThanOrEqualTo(HyperionExerciseTitleSanitizer.MAX_TITLE_LENGTH).doesNotEndWith(" ");
        assertThat(sanitized).isEqualTo("Implementing a thread safe bounded stack with generics and");
    }

    @Test
    void capsASingleWordTooLongToBreak() {
        String sanitized = HyperionExerciseTitleSanitizer.sanitize("a".repeat(200));

        assertThat(sanitized).hasSize(HyperionExerciseTitleSanitizer.MAX_TITLE_LENGTH);
        assertThat(TITLE_NAME_PATTERN.matcher(sanitized).matches()).isTrue();
    }

    @Test
    void namesAnExerciseFromTheOpeningOfItsBriefWhenNoAnswerSurvives() {
        String title = HyperionExerciseTitleSanitizer.fromBriefOpening("Students practise generics and exception handling by implementing a bounded stack.");

        assertThat(title).isEqualTo("Students practise generics and exception handling");
        assertThat(TITLE_NAME_PATTERN.matcher(title).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "!!! ???", "ab" })
    void yieldsTheEmptyStringForABriefWithNoWordsToName(String brief) {
        assertThat(HyperionExerciseTitleSanitizer.fromBriefOpening(brief)).isEmpty();
    }

    @Test
    void appendsADisambiguatingSuffix() {
        assertThat(HyperionExerciseTitleSanitizer.withSuffix("Bounded Stack", 2)).isEqualTo("Bounded Stack 2");
    }

    @Test
    void shortensTheTitleRatherThanExceedingTheLengthCapWhenSuffixing() {
        String suffixed = HyperionExerciseTitleSanitizer.withSuffix("a".repeat(HyperionExerciseTitleSanitizer.MAX_TITLE_LENGTH), 17);

        assertThat(suffixed).hasSize(HyperionExerciseTitleSanitizer.MAX_TITLE_LENGTH).endsWith(" 17");
        assertThat(TITLE_NAME_PATTERN.matcher(suffixed).matches()).isTrue();
    }
}
