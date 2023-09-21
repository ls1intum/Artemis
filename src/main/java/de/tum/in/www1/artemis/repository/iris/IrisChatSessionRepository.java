package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository interface for managing {@link IrisChatSession} entities.
 * Extends Spring Data JPA's {@link JpaRepository}.
 * Provides custom queries for finding chat sessions based on different criteria.
 */
public interface IrisChatSessionRepository extends JpaRepository<IrisChatSession, Long> {

    /**
     * Finds a list of {@link IrisChatSession} based on the exercise and user IDs.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
                SELECT s
                FROM IrisChatSession s
                WHERE s.exercise.id = :exerciseId
                AND s.user.id = :userId
                ORDER BY s.creationDate DESC
            """)
    List<IrisChatSession> findByExerciseIdAndUserId(Long exerciseId, Long userId);

    /**
     * Finds a list of chat sessions or throws an exception if none are found.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of chat sessions.
     * @throws EntityNotFoundException if no sessions are found.
     */
    @NotNull
    default List<IrisChatSession> findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        var result = findByExerciseIdAndUserId(exerciseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Session");
        }
        return result;
    }

    /**
     * Finds a session by ID or throws an exception if not found.
     *
     * @param sessionId The ID of the chat session to find.
     * @return The found chat session.
     * @throws EntityNotFoundException if no session is found.
     */
    @NotNull
    default IrisChatSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

    /**
     * Finds the newest chat session for a given exercise and user ID or throws an exception if none are found.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return The newest chat session.
     * @throws EntityNotFoundException if no sessions are found.
     */
    default IrisChatSession findNewestByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        var result = findByExerciseIdAndUserId(exerciseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Session");
        }
        return result.get(0);
    }
}
