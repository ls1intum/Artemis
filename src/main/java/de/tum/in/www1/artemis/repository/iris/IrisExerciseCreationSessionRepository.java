package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseCreationSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository interface for managing {@link IrisExerciseCreationSession} entities. Provides custom queries for finding
 * exercise creation sessions based on different criteria.
 */
public interface IrisExerciseCreationSessionRepository extends JpaRepository<IrisExerciseCreationSession, Long> {

    /**
     * Finds a list of {@link IrisExerciseCreationSession} based on the exercise and user IDs.
     *
     * @param userId The ID of the user.
     * @return A list of {@link IrisExerciseCreationSession} sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
            FROM IrisCodeEditorSession s
            WHERE s.user.id = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisExerciseCreationSession> findByUserId(@Param("userId") Long userId);

    /**
     * Finds a list of {@link IrisExerciseCreationSession} or throws an exception if none are found.
     *
     * @param userId The ID of the user.
     * @return A list of exercise creation sessions.
     * @throws EntityNotFoundException if no sessions are found.
     */
    @NotNull
    default List<IrisExerciseCreationSession> findByUserIdElseThrow(long userId) throws EntityNotFoundException {
        var result = findByUserId(userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Exercise Creation Session");
        }
        return result;
    }

    /**
     * Finds a session by ID or throws an exception if not found.
     *
     * @param sessionId The ID of the exercise creation session to find.
     * @return The found exercise creation session.
     * @throws EntityNotFoundException if no session is found.
     */
    @NotNull
    default IrisExerciseCreationSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Exercise Creation Session", sessionId));
    }

    /**
     * Finds the newest exercise creation session for a given user ID or throws an exception if none is found.
     *
     * @param userId The ID of the user.
     * @return The newest exercise creation session for the given user ID.
     * @throws EntityNotFoundException if no session is found.
     */
    default IrisExerciseCreationSession findNewestByUserIdElseThrow(long userId) throws EntityNotFoundException {
        var result = findByUserId(userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Exercise Creation Session");
        }
        return result.get(0);
    }
}
