package de.tum.cit.aet.artemis.repository.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the TestwiseCoverageReportEntry entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TestwiseCoverageReportEntryRepository extends ArtemisJpaRepository<TestwiseCoverageReportEntry, Long> {

}
