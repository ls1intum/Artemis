package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Minimal exam exercise-group reference carried on exam text-exercise responses. Several unchanged Angular views read
 * this nested shape: exam mode is detected from the presence of {@code exercise.exerciseGroup}; the student editor reads
 * {@code exam.publishResultsDate} (and {@code exam.exampleSolutionPublicationDate}) for post-publish behavior; the
 * management screens read {@code exam.course} for course context and access rights, {@code exam.title} for the detail
 * exam link, {@code exam.testExam} to gate feedback-suggestion options, and {@code exam.numberOfCorrectionRoundsInExam}
 * for the assessment controls. Flat exam ids are not enough.
 *
 * @param id   the exercise group id
 * @param exam the minimal exam reference the client reads
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseExamGroupDTO(Long id, ExamReferenceDTO exam) implements Serializable {

    /**
     * Minimal exam reference carrying the fields the unchanged client reads off {@code exercise.exerciseGroup.exam}.
     *
     * @param id                             the exam id
     * @param title                          the exam title (detail-page exam link)
     * @param testExam                       whether this is a test exam (gates feedback-suggestion options)
     * @param publishResultsDate             when exam results are published (post-publish behavior)
     * @param exampleSolutionPublicationDate when the example solution becomes visible
     * @param numberOfCorrectionRoundsInExam number of correction rounds (assessment controls)
     * @param course                         light course projection (id, title, group names, ...); {@code null} when the
     *                                           exam's course is not loaded
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamReferenceDTO(Long id, String title, Boolean testExam, ZonedDateTime publishResultsDate, ZonedDateTime exampleSolutionPublicationDate,
            Integer numberOfCorrectionRoundsInExam, CourseForQuizExerciseDTO course) implements Serializable {
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
        ExamReferenceDTO examRef = new ExamReferenceDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.getPublishResultsDate(), exam.getExampleSolutionPublicationDate(),
                exam.getNumberOfCorrectionRoundsInExam(), course);
        return new TextExerciseExamGroupDTO(exerciseGroup.getId(), examRef);
    }
}
