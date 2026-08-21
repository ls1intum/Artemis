package de.tum.cit.aet.artemis.lti.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.UserLti;

@Conditional(LtiEnabled.class)
@Lazy
@Repository
public interface UserLtiRepository extends ArtemisJpaRepository<UserLti, Long> {

    /**
     * Whether the launch provisioned this account. An account with no row was not created by a launch.
     *
     * @param userId the account
     * @return true if a row marks the account as launch-created
     */
    boolean existsByUserIdAndCreatedByLaunchIsTrue(long userId);
}
