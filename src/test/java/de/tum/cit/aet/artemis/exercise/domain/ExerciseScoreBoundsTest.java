package de.tum.cit.aet.artemis.exercise.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseScoreBoundsTest {

    private static TextExercise exerciseWithPoints(Double maxPoints, Double bonusPoints) {
        var exercise = new TextExercise();
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints);
        return exercise;
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "0, 10, 0", "100, 10, 0", "150, 10, 5", "110, 100, 10", "100.00004, 10, 0", "-0.00004, 10, 0", "133.33333333333334, 3, 1" })
    void scoreWithinBounds_isAccepted(double score, double maxPoints, double bonusPoints) {
        var exercise = exerciseWithPoints(maxPoints, bonusPoints);

        assertThatCode(() -> exercise.checkScoreWithinBoundsElseThrow(score)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "-0.0001, 10, 0", "-50, 10, 0", "100.0001, 10, 0", "100.00005, 10, 0", "150.0001, 10, 5", "99999, 100, 10" })
    void scoreOutOfBounds_isRejected(double score, double maxPoints, double bonusPoints) {
        var exercise = exerciseWithPoints(maxPoints, bonusPoints);

        assertThatThrownBy(() -> exercise.checkScoreWithinBoundsElseThrow(score)).isInstanceOf(BadRequestAlertException.class).hasFieldOrPropertyWithValue("errorKey",
                "scoreOutOfRange");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(doubles = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
    void nonFiniteScore_isRejected(double score) {
        var exercise = exerciseWithPoints(10.0, 0.0);

        assertThatThrownBy(() -> exercise.checkScoreWithinBoundsElseThrow(score)).isInstanceOf(BadRequestAlertException.class).hasFieldOrPropertyWithValue("errorKey",
                "scoreOutOfRange");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource(value = { "null, 0", "0, 0", "-1, 0", "NaN, 0", "Infinity, 0", "10, -1", "10, NaN", "10, Infinity",
            "1.7976931348623157E308, 1.7976931348623157E308" }, nullValues = "null")
    void invalidExercisePoints_failClosed(Double maxPoints, Double bonusPoints) {
        var exercise = exerciseWithPoints(maxPoints, bonusPoints);

        assertThatThrownBy(() -> exercise.checkScoreWithinBoundsElseThrow(50)).isInstanceOf(IllegalStateException.class);
    }
}
