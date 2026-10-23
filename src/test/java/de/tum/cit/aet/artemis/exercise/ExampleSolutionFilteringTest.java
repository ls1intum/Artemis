package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExampleSolutionFilteringTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-01-01T10:00:00Z");

    @ParameterizedTest
    @MethodSource("exercises")
    void unpublishedSolutionIsRemovedBeforeSharedGradingInformation(Exercise exercise) {
        var observed = spy(exercise);
        solution(observed, "Example solution");
        observed.setGradingInstructions("Private grading instructions");
        observed.setExampleSolutionPublicationDate(NOW);
        try (var clock = mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(ZonedDateTime::now).thenReturn(NOW);
            observed.filterSensitiveInformation();
        }
        var order = inOrder(observed);
        if (observed instanceof TextExercise text) {
            order.verify(text).setExampleSolution(null);
        }
        else {
            order.verify((FileUploadExercise) observed).setExampleSolution(null);
        }
        order.verify(observed).setGradingInstructions(null);
        order.verify(observed).setGradingCriteria(null);
        assertThat(solution(observed)).isNull();
        assertThat(observed.getGradingInstructions()).isNull();
        assertThat(observed.getGradingCriteria()).isNull();
        assertThat(JsonObjectMapper.get().valueToTree(observed).has("exampleSolution")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("exercises")
    void publishedSolutionRemainsWhileGradingInformationIsRemoved(Exercise exercise) {
        solution(exercise, "Example solution");
        exercise.setGradingInstructions("Private grading instructions");
        exercise.setExampleSolutionPublicationDate(NOW.minusNanos(1));
        try (var clock = mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(ZonedDateTime::now).thenReturn(NOW);
            exercise.filterSensitiveInformation();
        }
        assertThat(solution(exercise)).isEqualTo("Example solution");
        assertThat(exercise.getGradingInstructions()).isNull();
        assertThat(exercise.getGradingCriteria()).isNull();
    }

    @ParameterizedTest
    @MethodSource("exercises")
    void absentPublicationDateHidesSolution(Exercise exercise) {
        solution(exercise, "Example solution");
        exercise.filterSensitiveInformation();
        assertThat(solution(exercise)).isNull();
    }

    @ParameterizedTest
    @MethodSource("exercises")
    void examPublicationDateTakesPrecedenceOverExercisePublicationDate(Exercise exercise) {
        var exam = new Exam();
        var group = new ExerciseGroup();
        group.setExam(exam);
        exercise.setExerciseGroup(group);
        exercise.setExampleSolutionPublicationDate(NOW.minusDays(1));
        exam.setExampleSolutionPublicationDate(NOW.plusDays(1));
        solution(exercise, "Example solution");
        try (var clock = mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(ZonedDateTime::now).thenReturn(NOW);
            exercise.filterSensitiveInformation();
            assertThat(solution(exercise)).isNull();
            exam.setExampleSolutionPublicationDate(NOW.minusDays(1));
            exercise.setExampleSolutionPublicationDate(NOW.plusDays(1));
            solution(exercise, "Published exam solution");
            exercise.filterSensitiveInformation();
            assertThat(solution(exercise)).isEqualTo("Published exam solution");
        }
    }

    private static Stream<Exercise> exercises() {
        return Stream.of(new TextExercise(), new FileUploadExercise());
    }

    private static void solution(Exercise exercise, String solution) {
        if (exercise instanceof TextExercise text) {
            text.setExampleSolution(solution);
        }
        else {
            ((FileUploadExercise) exercise).setExampleSolution(solution);
        }
    }

    private static String solution(Exercise exercise) {
        return exercise instanceof TextExercise text ? text.getExampleSolution() : ((FileUploadExercise) exercise).getExampleSolution();
    }
}
