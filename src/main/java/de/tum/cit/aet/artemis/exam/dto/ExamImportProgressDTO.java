package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExamImportProgressState;
import de.tum.cit.aet.artemis.exam.domain.ExerciseImportStatus;

/**
 * Live progress of an exam (or exercise-group) import, sent to the importing user over a websocket so the UI can show a
 * progress bar and a per-exercise status while the (synchronous) import request is still running.
 * <p>
 * While {@link ExamImportProgressState#RUNNING}, {@code currentExerciseTitle} and {@code currentStatus} describe the most
 * recent per-exercise transition and the lists are empty. On the terminal {@code COMPLETED} / {@code COMPLETED_WITH_ISSUES}
 * event, the lists carry the full set of skipped and incomplete exercise titles (and the per-exercise fields are empty).
 *
 * @param state                the overall import state
 * @param totalExercises       the total number of exercises to import
 * @param processedExercises   the number of exercises processed so far (imported, skipped or incomplete)
 * @param currentExerciseTitle the title of the exercise the most recent event refers to (only set while running)
 * @param currentStatus        the status of that exercise (only set while running)
 * @param skippedExercises     titles of exercises that were cleanly skipped (only set on the terminal event)
 * @param incompleteExercises  titles of exercises that failed partway and may be incomplete (only set on the terminal event)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamImportProgressDTO(ExamImportProgressState state, int totalExercises, int processedExercises, String currentExerciseTitle, ExerciseImportStatus currentStatus,
        List<String> skippedExercises, List<String> incompleteExercises) {
}
