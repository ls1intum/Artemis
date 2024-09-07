package de.tum.in.www1.artemis.repository.cleanup;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.cleanup.CleanupJobExecution;
import de.tum.in.www1.artemis.domain.enumeration.CleanupJobType;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the CleanupJobExecution entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CleanupJobExecutionRepository extends ArtemisJpaRepository<CleanupJobExecution, Long> {

    CleanupJobExecution findTopByCleanupJobTypeOrderByDeletionTimestampDesc(CleanupJobType jobType);

}
