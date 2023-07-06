package de.tum.in.www1.artemis.repository.iris;

import java.util.List;
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

    @Query("""
                SELECT s
                FROM IrisChatSession s
                WHERE s.exercise.id = :exerciseId
                AND s.user.id = :userId
            """)
    Optional<List<IrisChatSession>> findByExerciseIdAndUserId(Long exerciseId, Long userId);

    @Query("""
                SELECT s
                FROM IrisChatSession s
                WHERE s.exercise.id = :exerciseId
                AND s.user.id = :userId
                ORDER BY s.creationDate DESC
                LIMIT 1
            """)
    Optional<IrisChatSession> findNewestByExerciseIdAndUserId(Long exerciseId, Long userId);

    @NotNull
    default List<IrisChatSession> findByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        return findByExerciseIdAndUserId(exerciseId, userId).orElseThrow(() -> new EntityNotFoundException("Iris Session"));
    }

    @NotNull
    default IrisChatSession findByIdElseThrow(long sessionId) throws EntityNotFoundException {
        return findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris Session", sessionId));
    }

    default IrisChatSession findNewestByExerciseIdAndUserIdElseThrow(long exerciseId, long userId) throws EntityNotFoundException {
        return findNewestByExerciseIdAndUserId(exerciseId, userId).orElseThrow(() -> new EntityNotFoundException("Iris Session"));
    }
}
