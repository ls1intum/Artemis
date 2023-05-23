package de.tum.in.www1.artemis.repository.iris;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisSession entity.
 */
public interface IrisSessionRepository extends JpaRepository<IrisSession, Long> {

    Optional<IrisSession> findByExerciseIdAndUserId(Long exerciseId, Long userId);

    @NotNull
    default IrisSession findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        return findByExerciseIdAndUserId(exerciseId, userId).orElseThrow(() -> new EntityNotFoundException("Iris Session"));
    }

    @NotNull
    default IrisSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

    @Query("""
            SELECT s
            FROM IrisSession s
            LEFT JOIN FETCH s.messages m
            WHERE s.id = :sessionId
            """)
    IrisSession findByIdWithMessages(long sessionId);

    @Query("""
            SELECT s
            FROM IrisSession s
            LEFT JOIN FETCH s.messages m
            LEFT JOIN FETCH m.content c
            WHERE s.id = :sessionId
            """)
    IrisSession findByIdWithMessagesAndContents(long sessionId);
}
