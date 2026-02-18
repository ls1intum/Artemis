package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineOrgUnit;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CampusOnlineOrgUnitRepository extends ArtemisJpaRepository<CampusOnlineOrgUnit, Long> {

    Optional<CampusOnlineOrgUnit> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}
