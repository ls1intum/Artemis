package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit tests for {@link Exercise#validateTitle()}, the shared title validation used by all exercise create/import and
 * edit flows (it is the exact point that rejected titles like "Lärche").
 */
class ExerciseTitleValidationTest {

    private static Exercise exerciseWithTitle(String title) {
        Exercise exercise = new TextExercise();
        exercise.setTitle(title);
        return exercise;
    }

    @ParameterizedTest
    @ValueSource(strings = { "Lärche", "Müller Übung", "naïve café", "Sør-Trøndelag", "Größe", "Algorithmen 1", "test_exercise-1" })
    void shouldAcceptTitlesWithUnicodeLettersAndAllowedCharacters(String title) {
        // Regression test for "Lärche": umlauts and other Unicode letters must be accepted in exercise titles. The
        // pattern was previously ASCII-only, which rejected such titles on edit (create/import used the same pattern).
        assertThatCode(() -> exerciseWithTitle(title).validateTitle()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "title<script>", "title$value", "title;drop", "100%done", "a/b" })
    void shouldRejectTitlesWithDisallowedSymbols(String title) {
        // Letters/marks/numbers, underscore, hyphen and whitespace are allowed; other symbols (which could break VCS,
        // headers, or rendering) must still be rejected.
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseWithTitle(title).validateTitle());
    }

    @Test
    void shouldRejectTitleThatIsTooShort() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseWithTitle("ab").validateTitle());
    }
}
