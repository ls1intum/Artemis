package de.tum.cit.aet.artemis.exam.service;

import java.util.List;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exam.domain.ExamImportProgressState;
import de.tum.cit.aet.artemis.exam.domain.ExerciseImportStatus;
import de.tum.cit.aet.artemis.exam.dto.ExamImportProgressDTO;

/**
 * Emits live progress of a single exam (or exercise-group) import to the importing user over a websocket.
 * <p>
 * One instance is created per import request. It is intentionally a no-op when no {@code importId} or user login is
 * available (e.g. the internal {@link ExamImportService#importExamWithExercises(de.tum.cit.aet.artemis.exam.domain.Exam, long)}
 * path used by course-material import), so the import behaves exactly as before when nobody is listening for progress.
 * <p>
 * Sending is best-effort: {@link WebsocketMessagingService#sendMessageToUser} never throws, so a websocket problem can
 * never interrupt the actual import.
 */
class ExamImportProgressNotifier {

    /**
     * Prefix of the user-scoped websocket destination the client subscribes to ({@code /user/topic/exam-import/{importId}}).
     */
    private static final String TOPIC_PREFIX = "/topic/exam-import/";

    private final WebsocketMessagingService websocketMessagingService;

    private final String userLogin;

    private final String topic;

    private final boolean active;

    private int totalExercises;

    private int processedExercises;

    ExamImportProgressNotifier(WebsocketMessagingService websocketMessagingService, String userLogin, String importId) {
        this.websocketMessagingService = websocketMessagingService;
        this.userLogin = userLogin;
        this.topic = importId == null ? null : TOPIC_PREFIX + importId;
        this.active = userLogin != null && importId != null;
    }

    /**
     * Announces the start of the import and the total number of exercises to process.
     *
     * @param totalExercises the total number of exercises that will be imported
     */
    void start(int totalExercises) {
        this.totalExercises = totalExercises;
        this.processedExercises = 0;
        sendRunning(null, null);
    }

    /**
     * Signals that the given exercise is now being imported (not yet counted as processed).
     *
     * @param exerciseTitle the title of the exercise being imported
     */
    void importing(String exerciseTitle) {
        sendRunning(exerciseTitle, ExerciseImportStatus.IMPORTING);
    }

    /**
     * Signals that the given exercise was imported successfully.
     *
     * @param exerciseTitle the title of the imported exercise
     */
    void imported(String exerciseTitle) {
        processedExercises++;
        sendRunning(exerciseTitle, ExerciseImportStatus.IMPORTED);
    }

    /**
     * Signals that the given exercise was cleanly skipped (nothing persisted).
     *
     * @param exerciseTitle the title of the skipped exercise
     */
    void skipped(String exerciseTitle) {
        processedExercises++;
        sendRunning(exerciseTitle, ExerciseImportStatus.SKIPPED);
    }

    /**
     * Signals that the given exercise failed partway and may be incomplete.
     *
     * @param exerciseTitle the title of the incomplete exercise
     */
    void incomplete(String exerciseTitle) {
        processedExercises++;
        sendRunning(exerciseTitle, ExerciseImportStatus.INCOMPLETE);
    }

    /**
     * Announces that the import finished, reporting the full set of skipped and incomplete exercises.
     *
     * @param skippedExercises    titles of exercises that were cleanly skipped
     * @param incompleteExercises titles of exercises that failed partway and may be incomplete
     */
    void finished(List<String> skippedExercises, List<String> incompleteExercises) {
        if (!active) {
            return;
        }
        ExamImportProgressState state = skippedExercises.isEmpty() && incompleteExercises.isEmpty() ? ExamImportProgressState.COMPLETED
                : ExamImportProgressState.COMPLETED_WITH_ISSUES;
        websocketMessagingService.sendMessageToUser(userLogin, topic,
                new ExamImportProgressDTO(state, totalExercises, processedExercises, null, null, skippedExercises, incompleteExercises));
    }

    private void sendRunning(String exerciseTitle, ExerciseImportStatus status) {
        if (!active) {
            return;
        }
        websocketMessagingService.sendMessageToUser(userLogin, topic,
                new ExamImportProgressDTO(ExamImportProgressState.RUNNING, totalExercises, processedExercises, exerciseTitle, status, List.of(), List.of()));
    }
}
