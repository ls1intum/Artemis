package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;

/**
 * API exposing chat-session cleanup to other feature modules (exercise, lecture, ...).
 * <p>
 * When an exercise or lecture is deleted, any Iris chat sessions referencing it must be
 * removed explicitly. The unified {@code IrisChatSession} entity stores {@code entityId} as
 * a plain long (no JPA relation), so there is no FK cascade to rely on.
 */
@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisChatSessionApi extends AbstractIrisApi {

    private final IrisChatSessionRepository irisChatSessionRepository;

    public IrisChatSessionApi(IrisChatSessionRepository irisChatSessionRepository) {
        this.irisChatSessionRepository = irisChatSessionRepository;
    }

    /**
     * Deletes all Iris chat sessions that belong to the given programming exercise.
     *
     * @param programmingExerciseId the id of the programming exercise
     */
    public void deleteAllForProgrammingExercise(long programmingExerciseId) {
        irisChatSessionRepository.deleteAllByChatModeAndEntityId(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, programmingExerciseId);
    }

    /**
     * Deletes all Iris chat sessions that belong to the given text exercise.
     *
     * @param textExerciseId the id of the text exercise
     */
    public void deleteAllForTextExercise(long textExerciseId) {
        irisChatSessionRepository.deleteAllByChatModeAndEntityId(IrisChatMode.TEXT_EXERCISE_CHAT, textExerciseId);
    }

    /**
     * Deletes all Iris chat sessions that belong to the given lecture.
     *
     * @param lectureId the id of the lecture
     */
    public void deleteAllForLecture(long lectureId) {
        irisChatSessionRepository.deleteAllByChatModeAndEntityId(IrisChatMode.LECTURE_CHAT, lectureId);
    }
}
