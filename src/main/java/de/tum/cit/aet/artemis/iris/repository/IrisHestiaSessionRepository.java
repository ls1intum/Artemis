package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisHestiaSession;

/**
 * Repository interface for managing {@link IrisHestiaSession} entities.
 * Provides custom queries for finding hestia sessions based on different criteria.
 */
@Repository
@Profile(PROFILE_IRIS)
public interface IrisHestiaSessionRepository extends ArtemisJpaRepository<IrisHestiaSession, Long> {

    /**
     * Finds a list of {@link IrisHestiaSession} based on the exercise and user IDs.
     *
     * @param codeHintId The ID of the code hint.
     * @return A list of hestia sessions sorted by creation date in descending order.
     */
    List<IrisHestiaSession> findByCodeHintIdOrderByCreationDateDesc(Long codeHintId);
}
