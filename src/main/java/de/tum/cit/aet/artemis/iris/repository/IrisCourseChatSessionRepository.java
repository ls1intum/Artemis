package de.tum.cit.aet.artemis.iris.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;

/**
 * Repository interface for managing {@link IrisCourseChatSession} entities.
 */
public interface IrisCourseChatSessionRepository extends ArtemisJpaRepository<IrisCourseChatSession, Long> {

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
            WHERE s.course.id = :courseId
                AND s.user.id = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisCourseChatSession> findSessionsByCourseIdAndUserId(@Param("courseId") long courseId, @Param("userId") long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisCourseChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    /**
     * Finds the latest chat sessions by course ID and user ID, including their messages, with pagination support.
     * This method avoids in-memory paging by retrieving the session IDs directly from the database.
     *
     * @param courseId the ID of the course to find the chat sessions for
     * @param userId   the ID of the user to find the chat sessions for
     * @param pageable the pagination information
     * @return a list of {@code IrisCourseChatSession} with messages, or an empty list if no sessions are found
     */
    default List<IrisCourseChatSession> findLatestByCourseIdAndUserIdWithMessages(long courseId, long userId, Pageable pageable) {
        List<Long> ids = findSessionsByCourseIdAndUserId(courseId, userId, pageable).stream().map(DomainObject::getId).toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSessionsWithMessagesByIdIn(ids);
    }

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
}
