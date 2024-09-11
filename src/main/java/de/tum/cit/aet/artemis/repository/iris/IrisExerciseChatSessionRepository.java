package de.tum.cit.aet.artemis.repository.iris;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.iris.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository interface for managing {@link IrisExerciseChatSession} entities.
 * Provides custom queries for finding chat sessions based on different criteria.
 */
public interface IrisExerciseChatSessionRepository extends ArtemisJpaRepository<IrisExerciseChatSession, Long> {

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
            WHERE s.exercise.id = :exerciseId
                AND s.user.id = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisExerciseChatSession> findSessionsByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisExerciseChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    /**
     * Finds the latest chat sessions by exercise ID and user ID, including their messages, with pagination support.
     * This method avoids in-memory paging by retrieving the session IDs directly from the database.
     *
     * @param exerciseId the ID of the exercise to find the chat sessions for
     * @param userId     the ID of the user to find the chat sessions for
     * @param pageable   the pagination information
     * @return a list of {@code IrisExerciseChatSession} with messages, or an empty list if no sessions are found
     */
    default List<IrisExerciseChatSession> findLatestByExerciseIdAndUserIdWithMessages(Long exerciseId, Long userId, Pageable pageable) {
        List<Long> ids = findSessionsByExerciseIdAndUserId(exerciseId, userId, pageable).stream().map(DomainObject::getId).toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSessionsWithMessagesByIdIn(ids);
    }

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
            throw new EntityNotFoundException("Iris Exercise Chat Session");
        }
        return result;
    }
}
