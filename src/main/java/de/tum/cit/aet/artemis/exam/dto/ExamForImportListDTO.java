package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamMode;

/**
 * Slim exam row for the paged exam-import search table, returned inside the
 * {@link de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO} of {@code GET exams}
 * ({@link de.tum.cit.aet.artemis.exam.web.ExamResource#getAllExamsOnPage}).
 * <p>
 * The only consumer is the exam-import modal table ({@code exam-import.component}, via
 * {@code ExamImportPagingService}). Each row renders exactly four fields: {@code exam.id} (# column and
 * search-highlight), {@code exam.title}, {@code exam.course.title} (Course column), and {@code exam.examMode}
 * (real-vs-test-exam badge). On "Import" / "Select exercise group" the row is used only by {@code exam.id} to route to
 * the detail import fetch ({@link ExamResource#getExamForImportWithExercises}); no other field is read off the paged
 * row. The paged results are not passed through the exam response converter, so no course access-rights / date fields
 * are needed here.
 * <p>
 * The course is embedded via {@link CourseForExamDTO} (already the exam module's canonical slim-course projection); only
 * its {@code title} is read on this screen, the other fields it carries are harmless and cost nothing extra to map from
 * the already-loaded (default-EAGER {@code @ManyToOne}) course.
 *
 * @param id       the id of the exam
 * @param title    the title of the exam
 * @param examMode the mode of the exam (drives the exam-mode badge)
 * @param course   the slim course projection (only {@code title} is read on the import table)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForImportListDTO(long id, @Nullable String title, ExamMode examMode, @Nullable CourseForExamDTO course) {

    /**
     * Builds a slim import-table row from an exam. Reads the two scalar columns, the {@code examMode} enum and the eager
     * course, so it is safe on the detached entities the paged query returns.
     *
     * @param exam the exam to convert (with its eager course loaded)
     * @return the slim import-table row
     */
    public static ExamForImportListDTO of(Exam exam) {
        return new ExamForImportListDTO(exam.getId(), exam.getTitle(), exam.getExamMode(), CourseForExamDTO.of(exam.getCourse()));
    }
}
