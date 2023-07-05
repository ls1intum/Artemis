package de.tum.in.www1.artemis.repository.iris;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisChatSession entity.
 */
public interface IrisChatSessionRepository extends JpaRepository<IrisChatSession, Long> {

    Optional<IrisChatSession> findByExerciseIdAndUserId(Long exerciseId, Long userId);

    @Query("""
            SELECT DISTINCT s
            FROM IrisChatSession s, IrisSessionExerciseConnector c
            WHERE s.exercise.id = :exerciseId
            AND s.user.id = :userId
            AND s.id = c.session.id
            ORDER BY c.creationDate DESC
            LIMIT 1
            """)
    Optional<IrisChatSession> findNewestByExerciseIdAndUserId(Long exerciseId, Long userId);

    @NotNull
    default IrisChatSession findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        return findByExerciseIdAndUserId(exerciseId, userId).orElseThrow(() -> new EntityNotFoundException("Iris Session"));
    }

    @NotNull
    default IrisChatSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

    @NotNull
    default IrisChatSession findNewestByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        return findNewestByExerciseIdAndUserId(exerciseId, userId).orElseThrow(() -> new EntityNotFoundException("Iris Session"));
    }
}
