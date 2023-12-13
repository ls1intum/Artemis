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
     * @param userId   The ID of the user.
     * @param courseId the ID of the course
     * @return A list of {@link IrisExerciseCreationSession} sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
            FROM IrisExerciseCreationSession s
            WHERE s.user.id = :userId
                AND s.course.id = :courseId
            ORDER BY s.creationDate DESC
            """)
    List<IrisExerciseCreationSession> findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /**
     * Finds a list of {@link IrisExerciseCreationSession} or throws an exception if none are found.
     *
     * @param userId The ID of the user.
     * @return A list of exercise creation sessions.
     * @throws EntityNotFoundException if no sessions are found.
     */
    @NotNull
    default List<IrisExerciseCreationSession> findByCourseIdAndUserIdElseThrow(long courseId, long userId) throws EntityNotFoundException {
        var result = findByCourseIdAndUserId(courseId, userId);
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
     * Finds the newest code editor session for a given exercise and user ID or throws an exception if none is found.
     *
     * @param courseId The ID of the exercise.
     * @param userId   The ID of the user.
     * @return The newest code editor session for the given exercise and user ID.
     * @throws EntityNotFoundException if no session is found.
     */
    default IrisExerciseCreationSession findNewestByExerciseIdAndUserIdElseThrow(long courseId, long userId) throws EntityNotFoundException {
        var result = findByCourseIdAndUserId(courseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Code Editor Session");
        }
        return result.get(0);
    }
}
