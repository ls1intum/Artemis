package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Minimal exam projection returned by {@code GET courses/{courseId}/exams-for-user}
 * ({@link de.tum.cit.aet.artemis.exam.web.ExamResource#getExamsWithQuizExercisesForUser}).
 * <p>
 * That endpoint feeds a single consumer: the "add existing questions from an exam" picker in the quiz editor
 * ({@code quiz-question-list-edit-existing.component}). It renders the returned exams as a dropdown of
 * {@code <option [value]="exam.id">{{ exam.title }}</option>} and, on selection, looks the exam up again purely by
 * {@code exam.id} to fetch its quiz exercises via a separate call. It reads no other field.
 * <p>
 * The endpoint's fetch eagerly loads the exam's exercise groups, quiz exercises and (via the default-EAGER
 * {@code @ManyToOne}) course, but none of that graph is read by the picker, so this DTO carries only the id and title —
 * the whole point of the migration is to stop serializing that heavy, unused graph.
 *
 * @param id    the id of the exam (option value / lookup key)
 * @param title the title of the exam (option label)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForQuestionPoolDTO(long id, @Nullable String title) {

    /**
     * Builds the minimal picker projection from an exam. Reads only the two stored scalar columns, so it is safe on the
     * detached entity the resource returns outside a transaction.
     *
     * @param exam the exam to convert
     * @return the minimal exam projection
     */
    public static ExamForQuestionPoolDTO of(Exam exam) {
        return new ExamForQuestionPoolDTO(exam.getId(), exam.getTitle());
    }
}
