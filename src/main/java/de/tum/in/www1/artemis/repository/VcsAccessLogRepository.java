package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.vcstokens.VcsAccessLog;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.isDeleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public interface VcsAccessLogRepository extends ArtemisJpaRepository<VcsAccessLog, Long> {

}
