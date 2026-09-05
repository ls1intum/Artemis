package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * Pins the denormalization of {@code complaint.exercise_id}.
 * <p>
 * The complaint counts on the assessment dashboards filter that column instead of joining {@code result}, so every
 * complaint has to carry the exercise of the result it belongs to. Both ways of attaching the result therefore copy
 * it: forgetting one would silently produce complaints that no dashboard counts.
 */
class ComplaintTest {

    private static final long EXERCISE_ID = 42L;

    @Test
    void shouldTakeTheExerciseIdFromTheResultPassedToTheSetter() {
        Complaint complaint = new Complaint();

        complaint.setResult(resultOfExercise());

        assertThat(complaint.getExerciseId()).isEqualTo(EXERCISE_ID);
    }

    @Test
    void shouldTakeTheExerciseIdFromTheResultPassedToTheBuilder() {
        Complaint complaint = new Complaint().result(resultOfExercise());

        assertThat(complaint.getExerciseId()).isEqualTo(EXERCISE_ID);
    }

    @Test
    void shouldKeepTheExerciseIdWhenTheResultIsDetached() {
        Complaint complaint = new Complaint().result(resultOfExercise());

        complaint.setResult(null);

        assertThat(complaint.getExerciseId()).isEqualTo(EXERCISE_ID);
    }

    private Result resultOfExercise() {
        Result result = new Result();
        result.setExerciseId(EXERCISE_ID);
        return result;
    }
}
