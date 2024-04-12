package de.tum.in.www1.artemis.repository.iris;

import java.util.Optional;

import jakarta.annotation.Nonnull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisSession entity.
 */
public interface IrisSessionRepository extends JpaRepository<IrisSession, Long> {

    @Query("""
            SELECT s
            FROM IrisSession s
                LEFT JOIN FETCH s.messages m
            WHERE s.id = :sessionId
            """)
    Optional<IrisSession> findByIdWithMessages(@Param("sessionId") long sessionId);

    @Query("""
            SELECT s
            FROM IrisSession s
                LEFT JOIN FETCH s.messages m
                LEFT JOIN FETCH m.content c
            WHERE s.id = :sessionId
            """)
    IrisSession findByIdWithMessagesAndContents(@Param("sessionId") long sessionId);

    @Nonnull
    default IrisSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

    @Nonnull
    default IrisSession findByIdWithMessagesElseThrow(long sessionId) throws EntityNotFoundException {
        return findByIdWithMessages(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

}
