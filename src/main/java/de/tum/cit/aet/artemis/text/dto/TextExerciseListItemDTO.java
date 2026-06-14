package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseRefDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Thin read DTO for listing {@link TextExercise} instances (course/exercise list and the cross-course import search).
 * Omits participations and other heavy associations, but carries a light {@code course} reference (id + title) so the
 * client's {@code courseTitle} pipe can render the course column.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseListItemDTO(Long id, String title, String shortName, String type, ExerciseType exerciseType, ZonedDateTime releaseDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, Double maxPoints, Set<String> categories, Set<GradingCriterionDTO> gradingCriteria, Long courseId, CourseRefDTO course, Long examId,
        String examTitle) implements Serializable {

    /**
     * Creates a {@link TextExerciseListItemDTO} from the given {@link TextExercise}.
     *
     * @param exercise the text exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static TextExerciseListItemDTO of(TextExercise exercise) {
        if (exercise == null) {
            return null;
        }

        Long courseId = null;
        Long examId = null;
        String examTitle = null;

        if (exercise.isExamExercise()) {
            Exam exam = exercise.getExam();
            if (exam != null) {
                examId = exam.getId();
                examTitle = exam.getTitle();
            }
        }
        else {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (course != null) {
                courseId = course.getId();
            }
        }

        Set<GradingCriterionDTO> gradingCriterionDTOs;
        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        else {
            gradingCriterionDTOs = null;
        }

        // Light course reference (id + title) so the client courseTitle pipe can render the course column in the list and
        // the cross-course import search; resolves the course for both course and exam exercises.
        CourseRefDTO course = CourseRefDTO.from(exercise.getCourseViaExerciseGroupOrCourseMember());

        return new TextExerciseListItemDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getType(), exercise.getExerciseType(),
                exercise.getReleaseDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getMaxPoints(), exercise.getCategories(), gradingCriterionDTOs,
                courseId, course, examId, examTitle);
    }
}
