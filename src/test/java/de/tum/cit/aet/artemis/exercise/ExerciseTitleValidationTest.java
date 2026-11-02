package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit tests for {@link Exercise#validateTitle()}, title validation shared by exercise creation, import and editing.
 */
class ExerciseTitleValidationTest {

    private static Exercise exerciseWithTitle(String title) {
        Exercise exercise = new TextExercise();
        exercise.setTitle(title);
        return exercise;
    }

    @ParameterizedTest
    @ValueSource(strings = { "Lärche", "Müller Übung", "naïve café", "Sør-Trøndelag", "Größe", "Algorithmen 1", "test_exercise-1",
            // non-Latin scripts are Unicode letters (\p{L}) and must be accepted, not just Latin accents
            "Тест-задача", "Άλγεβρα", "テスト課題" })
    void shouldAcceptTitlesWithUnicodeLettersAndAllowedCharacters(String title) {
        // Regression test for "Lärche": umlauts and other Unicode letters must be accepted in exercise titles. The
        // pattern was previously ASCII-only, which rejected such titles on edit (create/import used the same pattern).
        assertThatCode(() -> exerciseWithTitle(title).validateTitle()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "W01E01 Strategy vs. bridge pattern", "Exercise 1.2", "3. Übung" })
    void shouldAcceptPeriodsInNonProgrammingExerciseTitles(String title) {
        for (Exercise exercise : new Exercise[] { new TextExercise(), new ModelingExercise(), new FileUploadExercise() }) {
            exercise.setTitle(title);
            assertThatCode(exercise::validateTitle).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "W01E01 Strategy vs. bridge pattern", "title<script>", "title$value", "title;drop", "100%done", "a/b", "a\\b", "a:b", "a.b", "title|pipe" })
    void shouldKeepProgrammingExerciseTitleRestrictions(String title) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle(title);
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(exercise::validateTitle);
    }

    @ParameterizedTest
    @ValueSource(strings = { "title<script>", "title$value", "title;drop", "100%done", "a/b", "a\\b", "a:b", "title|pipe" })
    void shouldRejectTitlesWithDisallowedSymbols(String title) {
        // Display titles also allow periods; unsupported symbols must still be rejected.
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseWithTitle(title).validateTitle());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " ", "ab" })
    void shouldRejectMissingOrShortTitle(String title) {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseWithTitle(title).validateTitle());
    }

    @Test
    void getSanitizedExerciseTitleFallsBackToUniqueNameWhenTitleHasNoAsciiRepresentation() {
        // A title consisting only of non-ASCII letters sanitizes to an empty string; the sanitized title must still be
        // non-empty and unique (include the id) so two such exercises cannot export into the same directory.
        Exercise cjkExercise = exerciseWithTitle("テスト課題");
        cjkExercise.setId(42L);
        assertThat(cjkExercise.getSanitizedExerciseTitle()).isEqualTo("exercise_42");

        // a title that does have an ASCII representation is reduced as usual (not replaced by the fallback)
        Exercise umlautExercise = exerciseWithTitle("Lärche Übung");
        umlautExercise.setId(7L);
        assertThat(umlautExercise.getSanitizedExerciseTitle()).isEqualTo("Larche_Ubung");
    }
}
