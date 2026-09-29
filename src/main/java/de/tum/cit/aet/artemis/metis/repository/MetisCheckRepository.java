package de.tum.cit.aet.artemis.metis.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;

/**
 * Spring Data JPA repository for the {@link MetisCheck} entity, one row per student participation that has a Metis
 * comprehension check.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisCheckRepository extends ArtemisJpaRepository<MetisCheck, Long> {

    /**
     * The check of one participation. At most one exists, which the unique key on the participation guarantees.
     *
     * @param participationId the participation the check belongs to
     * @return the check, if one has been created for that participation
     */
    Optional<MetisCheck> findByParticipationId(long participationId);
}
