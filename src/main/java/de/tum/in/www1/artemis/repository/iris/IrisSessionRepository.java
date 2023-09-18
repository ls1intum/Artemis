package de.tum.in.www1.artemis.repository.iris;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
    Optional<IrisSession> findByIdWithMessages(long sessionId);

    @Query("""
            SELECT s
            FROM IrisSession s
            LEFT JOIN FETCH s.messages m
            LEFT JOIN FETCH m.content c
            WHERE s.id = :sessionId
            """)
    IrisSession findByIdWithMessagesAndContents(long sessionId);

    @NotNull
    default IrisSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

    @NotNull
    default IrisSession findByIdWithMessagesElseThrow(long sessionId) throws EntityNotFoundException {
        return findByIdWithMessages(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }
}
