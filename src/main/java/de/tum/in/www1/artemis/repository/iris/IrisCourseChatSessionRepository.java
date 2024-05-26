package de.tum.in.www1.artemis.repository.iris;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository interface for managing {@link IrisCourseChatSession} entities.
 * Extends Spring Data JPA's {@link JpaRepository}.
 * Provides custom queries for finding chat sessions based on different criteria.
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
    List<IrisCourseChatSession> findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
                SELECT s
                FROM IrisCourseChatSession s
                LEFT JOIN FETCH s.messages
                WHERE s.course.id = :courseId
                    AND s.user.id = :userId
                ORDER BY s.creationDate DESC
                LIMIT 1
            """)
    Optional<IrisCourseChatSession> findLatestByCourseIdAndUserIdWithMessages(@Param("courseId") Long courseId, @Param("userId") Long userId);

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
    default IrisCourseChatSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Chat Session", sessionId));
    }

    /**
     * Finds the latest chat session by course and user ID or throws an exception if not found.
     *
     * @param courseid The ID of the course.
     * @param userId   The ID of the user.
     * @return The latest chat session.
     * @throws EntityNotFoundException if no session is found.
     */
    @NotNull
    default IrisCourseChatSession findLatestByCourseIdAndUserIdWithMessagesElseThrow(long courseid, long userId) throws EntityNotFoundException {
        return this.findLatestByCourseIdAndUserIdWithMessages(courseid, userId).orElseThrow(() -> new EntityNotFoundException("Iris Chat Session"));
    }
}
