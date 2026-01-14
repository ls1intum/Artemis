package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;

/**
 * Repository interface for managing {@link IrisProgrammingExerciseChatSession} entities.
 * Provides custom queries for finding chat sessions based on different criteria.
 */
@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface IrisExerciseChatSessionRepository extends ArtemisJpaRepository<IrisProgrammingExerciseChatSession, Long> {

    /**
     * Finds a list of {@link IrisProgrammingExerciseChatSession} based on the exercise and user IDs.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of chat sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
                FROM IrisProgrammingExerciseChatSession s
                WHERE s.exerciseId = :exerciseId
                    AND s.userId = :userId
                ORDER BY s.creationDate DESC
            """)
    List<IrisProgrammingExerciseChatSession> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT s
            FROM IrisProgrammingExerciseChatSession s
            WHERE s.exerciseId = :exerciseId
                AND s.userId = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisProgrammingExerciseChatSession> findSessionsByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    Optional<IrisProgrammingExerciseChatSession> findSessionWithMessagesByIdAndUserId(Long id, Long userId);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisProgrammingExerciseChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    /**
     * Finds the latest chat sessions by exercise ID and user ID, including their messages, with pagination support.
     * This method avoids in-memory paging by retrieving the session IDs directly from the database.
     *
     * @param exerciseId the ID of the exercise to find the chat sessions for
     * @param userId     the ID of the user to find the chat sessions for
     * @param pageable   the pagination information
     * @return a list of {@code IrisProgrammingExerciseChatSession} with messages, or an empty list if no sessions are found
     */
    default List<IrisProgrammingExerciseChatSession> findLatestByExerciseIdAndUserIdWithMessages(Long exerciseId, Long userId, Pageable pageable) {
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
    @NonNull
    default List<IrisProgrammingExerciseChatSession> findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        var result = findByExerciseIdAndUserId(exerciseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Exercise Chat Session");
        }
        return result;
    }
}
