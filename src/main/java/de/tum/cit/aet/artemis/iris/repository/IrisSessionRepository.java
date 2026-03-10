package de.tum.cit.aet.artemis.iris.repository;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

/**
 * Spring Data repository for the IrisSession entity.
 */
@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisSessionRepository extends ArtemisJpaRepository<IrisSession, Long> {

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

    @NonNull
    default IrisSession findByIdWithMessagesElseThrow(long sessionId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithMessages(sessionId), sessionId);
    }

}
