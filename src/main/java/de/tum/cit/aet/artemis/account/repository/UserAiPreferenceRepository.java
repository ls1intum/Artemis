package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.account.domain.UserAiPreference;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserAiPreferenceRepository extends ArtemisJpaRepository<UserAiPreference, Long> {

    Optional<UserAiPreference> findByUserId(long userId);

    /**
     * Loads the preferences of several accounts at once.
     * <p>
     * Exists so that assembling a post with its answers resolves every author's decision in one query rather than one
     * per author.
     *
     * @param userIds the accounts to load
     * @return the rows that exist; accounts without one are simply absent
     */
    @Query("""
            SELECT preference
            FROM UserAiPreference preference
            WHERE preference.userId IN :userIds
            """)
    List<UserAiPreference> findAllByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
