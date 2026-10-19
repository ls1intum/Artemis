package de.tum.cit.aet.artemis.quiz.dto.exercise;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal course-title holder for {@link QuizExerciseForSearchDTO}. The {@code course} component name preserves the
 * wire shape the shared exercise import renderer reads ({@code course.title}); it carries no entity reference.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSearchCourseDTO(String title) {
}
