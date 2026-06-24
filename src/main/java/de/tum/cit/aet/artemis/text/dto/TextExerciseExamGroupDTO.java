package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Minimal exam exercise-group reference carried on exam text-exercise responses. The student text editor and exam
 * result summary detect exam mode from the presence of {@code exercise.exerciseGroup} and read
 * {@code exercise.exerciseGroup.exam.publishResultsDate} to decide post-publish behavior, and the management client
 * reads {@code exercise.exerciseGroup.exam.course} for the exam exercise's course context and access rights. The flat
 * exam ids are not enough because the unchanged client reads this nested shape.
 *
 * @param id   the exercise group id
 * @param exam the minimal exam reference (id, publish-results date, and course)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseExamGroupDTO(Long id, ExamReferenceDTO exam) implements Serializable {

    /**
     * Minimal exam reference: id, the publish-results date the client uses for post-publish behavior, and a light
     * course projection so the management client can resolve the exam exercise's course context and access rights via
     * {@code exercise.exerciseGroup.exam.course}.
     *
     * @param id                 the exam id
     * @param publishResultsDate when exam results are published
     * @param course             light course projection (id, title, group names, ...); {@code null} when the exam's
     *                               course is not loaded
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamReferenceDTO(Long id, ZonedDateTime publishResultsDate, CourseForQuizExerciseDTO course) implements Serializable {
    }

    /**
     * Builds the reference from an {@link ExerciseGroup}; expects the group (and its exam) to be loaded. The exam's
     * course is included only when it is already initialized (the detail endpoint resolves it during its access check);
     * otherwise it is left {@code null} to avoid forcing a lazy load on list/other paths.
     *
     * @param exerciseGroup the exercise group (may be {@code null})
     * @return the reference, or {@code null} if the input was {@code null}
     */
    public static TextExerciseExamGroupDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null) {
            return null;
        }
        Exam exam = exerciseGroup.getExam();
        if (exam == null) {
            return new TextExerciseExamGroupDTO(exerciseGroup.getId(), null);
        }
        CourseForQuizExerciseDTO course = Hibernate.isInitialized(exam.getCourse()) && exam.getCourse() != null ? CourseForQuizExerciseDTO.of(exam.getCourse()) : null;
        ExamReferenceDTO examRef = new ExamReferenceDTO(exam.getId(), exam.getPublishResultsDate(), course);
        return new TextExerciseExamGroupDTO(exerciseGroup.getId(), examRef);
    }
}
