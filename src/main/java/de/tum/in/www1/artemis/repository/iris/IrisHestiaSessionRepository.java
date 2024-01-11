package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;

/**
 * Repository interface for managing {@link IrisHestiaSession} entities.
 * Provides custom queries for finding hestia sessions based on different criteria.
 */
public interface IrisHestiaSessionRepository extends JpaRepository<IrisHestiaSession, Long> {

    /**
     * Finds a list of {@link IrisHestiaSession} based on the exercise and user IDs.
     *
     * @param codeHintId The ID of the code hint.
     * @return A list of hestia sessions sorted by creation date in descending order.
     */
    @Query("""
            SELECT s
            FROM IrisHestiaSession s
            WHERE s.codeHint.id = :codeHintId
            ORDER BY s.creationDate DESC
            """)
    List<IrisHestiaSession> findByCodeHintId(Long codeHintId);

    @Query("""
            SELECT s
            FROM IrisHestiaSession s
            LEFT JOIN FETCH s.messages m
            LEFT JOIN FETCH m.content c
            LEFT JOIN FETCH s.codeHint ch
            LEFT JOIN FETCH ch.exercise e
            LEFT JOIN FETCH ch.solutionEntries se
            WHERE s.id = :sessionId
            """)
    IrisHestiaSession findByIdWithMessagesAndContentsAndCodeHint(long sessionId);
}
