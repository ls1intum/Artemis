package de.tum.cit.aet.artemis.course.dto;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * A course and its quiz-exercise references for the existing-question selector.
 *
 * @param id        the course identifier
 * @param title     the course title
 * @param exercises the initialized quiz-exercise references
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForQuizSelectionDTO(long id, String title, Set<CourseQuizExerciseReferenceDTO> exercises) {

    /**
     * Maps a course and only its initialized quiz exercises.
     *
     * @param course the course to map
     * @return the quiz-selection response
     */
    public static CourseForQuizSelectionDTO of(Course course) {
        Set<CourseQuizExerciseReferenceDTO> exercises = Hibernate.isInitialized(course.getExercises()) ? course.getExercises().stream().filter(QuizExercise.class::isInstance)
                .map(QuizExercise.class::cast).map(CourseQuizExerciseReferenceDTO::of).collect(Collectors.toUnmodifiableSet()) : Set.of();
        return new CourseForQuizSelectionDTO(course.getId(), course.getTitle(), exercises);
    }
}
