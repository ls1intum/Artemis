package de.tum.in.www1.artemis.repository.iris;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseChatSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository interface for managing {@link IrisExerciseChatSession} entities.
 * Extends Spring Data JPA's {@link JpaRepository}.
 * Provides custom queries for finding chat sessions based on different criteria.
 */
public interface IrisExerciseChatSessionRepository extends JpaRepository<IrisExerciseChatSession, Long> {

    /**
     * Finds a list of {@link IrisExerciseChatSession} based on the exercise and user IDs.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
            FROM IrisExerciseChatSession s
            WHERE s.exercise.id = :exerciseId
                AND s.user.id = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisExerciseChatSession> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
                SELECT s
                FROM IrisExerciseChatSession s
                LEFT JOIN FETCH s.messages
                WHERE s.exercise.id = :exerciseId
                    AND s.user.id = :userId
                ORDER BY s.creationDate DESC
                LIMIT 1
            """)
    Optional<IrisExerciseChatSession> findLatestByExerciseIdAndUserIdWithMessages(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    /**
     * Finds a list of chat sessions or throws an exception if none are found.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of chat sessions.
     * @throws EntityNotFoundException if no sessions are found.
     */
    @NotNull
    default List<IrisExerciseChatSession> findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        var result = findByExerciseIdAndUserId(exerciseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Chat Session");
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
    default IrisExerciseChatSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Chat Session", sessionId));
    }

    /**
     * Finds the latest chat session by exercise and user ID or throws an exception if not found.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return The latest chat session.
     * @throws EntityNotFoundException if no session is found.
     */
    @NotNull
    default IrisExerciseChatSession findLatestByExerciseIdAndUserIdWithMessagesElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        return this.findLatestByExerciseIdAndUserIdWithMessages(exerciseId, userId).orElseThrow(() -> new EntityNotFoundException("Iris Chat Session"));
    }
}
