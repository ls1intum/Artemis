package de.tum.cit.aet.artemis.programming.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_LOCALVC)
@Lazy
@Repository
public interface VcsAnalyticsLogRepository extends ArtemisJpaRepository<VcsAnalyticsLog, Long> {

}
