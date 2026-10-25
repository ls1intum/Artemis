package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/** Course or exam context shared by exercise detail responses, retaining their existing nested group projections. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseResponseContextDTO<G>(Long courseId, Double courseAccuracyOfScores, CourseForQuizExerciseDTO course, Long exerciseGroupId, Long examId,
        ZonedDateTime examPublishResultsDate, G exerciseGroup) {

    /**
     * Maps exam context only for exam exercises, and direct course context otherwise.
     *
     * @param <G>         nested group projection type used by the endpoint
     * @param exercise    exercise to map
     * @param groupMapper endpoint-specific exercise group projection
     * @return the mutually exclusive course or exam context
     */
    public static <G> ExerciseResponseContextDTO<G> of(Exercise exercise, Function<ExerciseGroup, G> groupMapper) {
        if (exercise.isExamExercise()) {
            ExerciseGroup group = exercise.getExerciseGroup();
            Exam exam = exercise.getExam();
            return new ExerciseResponseContextDTO<>(null, null, null, group != null ? group.getId() : null, exam != null ? exam.getId() : null,
                    exam != null ? exam.getPublishResultsDate() : null, groupMapper.apply(group));
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return new ExerciseResponseContextDTO<>(course != null ? course.getId() : null,
                course != null && course.getAccuracyOfScores() != null ? course.getAccuracyOfScores().doubleValue() : null, CourseForQuizExerciseDTO.of(course), null, null, null,
                null);
    }
}
