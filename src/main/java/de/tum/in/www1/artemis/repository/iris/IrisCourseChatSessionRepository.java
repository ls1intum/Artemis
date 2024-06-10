package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository interface for managing {@link IrisCourseChatSession} entities.
 */
public interface IrisCourseChatSessionRepository extends JpaRepository<IrisCourseChatSession, Long> {

    /**
     * Finds a list of {@link IrisCourseChatSession} based on the exercise and user IDs.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
            FROM IrisCourseChatSession s
            WHERE s.course.id = :courseId
                AND s.user.id = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisCourseChatSession> findByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId);

    @Query("""
                SELECT s
                FROM IrisCourseChatSession s
                LEFT JOIN FETCH s.messages
                WHERE s.course.id = :courseId
                    AND s.user.id = :userId
                ORDER BY s.creationDate DESC
            """)
    List<IrisCourseChatSession> findLatestByCourseIdAndUserIdWithMessages(@Param("courseId") long courseId, @Param("userId") long userId, Pageable pageable);

    /**
     * Finds a list of chat sessions or throws an exception if none are found.
     *
     * @param courseId The ID of the course.
     * @param userId   The ID of the user.
     * @return A list of chat sessions.
     * @throws EntityNotFoundException if no sessions are found.
     */
    @NotNull
    default List<IrisCourseChatSession> findByExerciseIdAndUserIdElseThrow(long courseId, long userId) throws EntityNotFoundException {
        var result = findByCourseIdAndUserId(courseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Course Chat Session");
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
    default IrisCourseChatSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Chat Session", sessionId));
    }
}
