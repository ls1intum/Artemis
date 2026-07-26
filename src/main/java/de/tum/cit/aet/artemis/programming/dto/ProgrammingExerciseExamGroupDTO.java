package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Minimal exam exercise-group reference carried on exam programming-exercise responses.
 * <p>
 * Several unchanged Angular views read this nested shape: exam mode is detected from the presence of
 * {@code exercise.exerciseGroup}; the detail page and the navigation utils read
 * {@code exerciseGroup.exam.course.id}; the update form reads {@code exam.course.defaultProgrammingLanguage}; the
 * grading tab reads {@code exerciseGroup.exam.exampleSolutionPublicationDate}; {@code exam.testExam} gates the
 * feedback-suggestion options and {@code exam.numberOfCorrectionRoundsInExam} drives the assessment controls. Flat
 * exam ids are not enough.
 *
 * @param id   the exercise group id
 * @param exam the minimal exam reference the client reads
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseExamGroupDTO(Long id, ProgrammingExerciseExamDTO exam) implements Serializable {

    /**
     * Minimal exam reference carrying the fields the unchanged client reads off {@code exercise.exerciseGroup.exam}.
     *
     * @param id                             the exam id
     * @param title                          the exam title (detail-page exam link)
     * @param testExam                       whether this is a test exam (gates feedback-suggestion options)
     * @param publishResultsDate             when exam results are published (post-publish behavior)
     * @param exampleSolutionPublicationDate when the example solution becomes visible (grading tab)
     * @param numberOfCorrectionRoundsInExam number of correction rounds (assessment controls)
     * @param course                         light course projection; {@code null} when the exam's course is not loaded
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgrammingExerciseExamDTO(Long id, String title, Boolean testExam, ZonedDateTime publishResultsDate, ZonedDateTime exampleSolutionPublicationDate,
            Integer numberOfCorrectionRoundsInExam, ProgrammingExerciseCourseDTO course) implements Serializable {
    }

    /**
     * Builds the reference from an {@link ExerciseGroup}; expects the group (and its exam) to be loaded. The exam's
     * course is included only when it is already initialized, so this never forces a lazy load.
     *
     * @param exerciseGroup the exercise group (may be {@code null})
     * @return the reference, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseExamGroupDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null || !Hibernate.isInitialized(exerciseGroup)) {
            return null;
        }
        Exam exam = exerciseGroup.getExam();
        if (exam == null || !Hibernate.isInitialized(exam)) {
            return new ProgrammingExerciseExamGroupDTO(exerciseGroup.getId(), null);
        }
        ProgrammingExerciseCourseDTO course = Hibernate.isInitialized(exam.getCourse()) ? ProgrammingExerciseCourseDTO.of(exam.getCourse()) : null;
        ProgrammingExerciseExamDTO examReference = new ProgrammingExerciseExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.getPublishResultsDate(),
                exam.getExampleSolutionPublicationDate(), exam.getNumberOfCorrectionRoundsInExam(), course);
        return new ProgrammingExerciseExamGroupDTO(exerciseGroup.getId(), examReference);
    }
}
