package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

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
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;

/**
 * Repository interface for managing {@link IrisTextExerciseChatSession} entities.
 * Provides custom queries for finding text exercise chat sessions based on different criteria.
 */
@Profile(PROFILE_IRIS)
@Lazy
@Repository
public interface IrisTextExerciseChatSessionRepository extends ArtemisJpaRepository<IrisTextExerciseChatSession, Long> {

    /**
     * Finds a list of {@link IrisTextExerciseChatSession} based on the exercise and user IDs.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of text exercise chat sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
                FROM IrisTextExerciseChatSession s
                WHERE s.exerciseId = :exerciseId
                    AND s.userId = :userId
                ORDER BY s.creationDate DESC
            """)
    List<IrisTextExerciseChatSession> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT s
            FROM IrisTextExerciseChatSession s
            WHERE s.exerciseId = :exerciseId
                AND s.userId = :userId
            ORDER BY s.creationDate DESC
            """)
    List<IrisTextExerciseChatSession> findSessionsByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    Optional<IrisTextExerciseChatSession> findSessionWithMessagesByIdAndUserId(Long id, Long userId);

    @EntityGraph(type = LOAD, attributePaths = "messages")
    List<IrisTextExerciseChatSession> findSessionsWithMessagesByIdIn(List<Long> ids);

    /**
     * Finds the latest text exercise chat sessions by exercise ID and user ID, including their messages, with pagination support.
     * This method avoids in-memory paging by retrieving the session IDs directly from the database.
     *
     * @param exerciseId the ID of the exercise to find the text exercise chat sessions for
     * @param userId     the ID of the user to find the text exercise chat sessions for
     * @param pageable   the pagination information
     * @return a list of {@code IrisTextExerciseChatSession} with messages, or an empty list if no sessions are found
     */
    default List<IrisTextExerciseChatSession> findLatestByExerciseIdAndUserIdWithMessages(Long exerciseId, Long userId, Pageable pageable) {
        List<Long> ids = findSessionsByExerciseIdAndUserId(exerciseId, userId, pageable).stream().map(DomainObject::getId).toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return findSessionsWithMessagesByIdIn(ids);
    }

    /**
     * Finds a list of text exercise chat sessions or throws an exception if none are found.
     *
     * @param exerciseId The ID of the exercise.
     * @param userId     The ID of the user.
     * @return A list of text exercise chat sessions.
     * @throws EntityNotFoundException if no sessions are found.
     */
    @NotNull
    default List<IrisTextExerciseChatSession> findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        var result = findByExerciseIdAndUserId(exerciseId, userId);
        if (result.isEmpty()) {
            throw new EntityNotFoundException("Iris Text Exercise Chat Session");
        }
        return result;
    }
}
