package de.tum.cit.aet.artemis.core.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.core.domain.CleanupJobType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the CleanupJobExecution entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CleanupJobExecutionRepository extends ArtemisJpaRepository<CleanupJobExecution, Long> {

    CleanupJobExecution findTopByCleanupJobTypeOrderByDeletionTimestampDesc(CleanupJobType jobType);

}
