package de.tum.cit.aet.artemis.lti.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Whether the account was provisioned by a launch and has not completed its initialisation yet.
     *
     * @param userId the account
     * @return true if the account still has to be initialised
     */
    boolean existsByUserIdAndCreatedByLaunchIsTrueAndInitializedIsFalse(long userId);

    /**
     * Marks the account as initialised, but only if a launch provisioned it and it has not been initialised before.
     * <p>
     * A single conditional statement so that exactly one caller can win: initialisation hands out a password, and two
     * concurrent requests must not each get one. It is also what keeps a deactivated account out - such an account was
     * initialised earlier, so the marker is already true and the claim fails.
     *
     * @param userId the account
     * @return 1 if this caller claimed the initialisation, 0 if there was nothing to claim
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserLti lti
            SET lti.initialized = TRUE
            WHERE lti.userId = :userId
                AND lti.createdByLaunch = TRUE
                AND lti.initialized = FALSE
            """)
    int claimInitialization(@Param("userId") long userId);
}
