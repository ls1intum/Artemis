package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineOrgUnit;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for {@link CampusOnlineOrgUnit} entities.
 * Provides CRUD operations and lookup methods for CAMPUSOnline organizational units.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CampusOnlineOrgUnitRepository extends ArtemisJpaRepository<CampusOnlineOrgUnit, Long> {

    /**
     * Finds an organizational unit by its external CAMPUSOnline ID.
     *
     * @param externalId the external ID of the organizational unit
     * @return the org unit if found
     */
    Optional<CampusOnlineOrgUnit> findByExternalId(String externalId);

    /**
     * Checks whether an organizational unit with the given external ID exists.
     *
     * @param externalId the external ID to check
     * @return true if an org unit with this external ID exists
     */
    boolean existsByExternalId(String externalId);
}
