package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Result of importing exercise groups into an existing exam.
 * <p>
 * As with the full exam import, failing exercises do not abort the operation and are reported in two categories:
 * {@code skippedExercises} (cleanly skipped, nothing persisted) and {@code incompleteExercises} (failed partway, may
 * have left an incomplete exercise that should be reviewed/removed). See {@link ExamImportResultDTO} for details.
 *
 * @param exerciseGroups      all exercise groups of the target exam after the import
 * @param skippedExercises    titles of exercises that were cleanly skipped (nothing persisted)
 * @param incompleteExercises titles of exercises that failed partway and may be incomplete (need review)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupImportResultDTO(List<ExerciseGroup> exerciseGroups, List<String> skippedExercises, List<String> incompleteExercises) {
}
