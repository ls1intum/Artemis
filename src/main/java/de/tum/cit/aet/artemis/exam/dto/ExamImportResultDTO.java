package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Result of importing an exam together with its exercises.
 * <p>
 * The import is resilient: a single failing exercise does not abort the whole import. Failures are reported in two
 * categories so the client can give precise feedback:
 * <ul>
 * <li>{@code skippedExercises}: exercises that could not be imported and left no trace (e.g. the source exercise was
 * deleted or the responsible import module is unavailable). These were cleanly skipped.</li>
 * <li>{@code incompleteExercises}: exercises whose import failed partway and may have left an incomplete exercise in
 * the exam (e.g. a programming exercise whose repository copy failed after its entity was already created). These
 * should be reviewed and removed by the instructor.</li>
 * </ul>
 *
 * @param exam                the imported exam, containing the exercise groups and exercises that were imported successfully
 * @param skippedExercises    titles of exercises that were cleanly skipped (nothing persisted)
 * @param incompleteExercises titles of exercises that failed partway and may be incomplete (need review)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamImportResultDTO(Exam exam, List<String> skippedExercises, List<String> incompleteExercises) {
}
