package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.CalendarSubscriptionTokenStore;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CalendarSubscriptionTokenStoreRepository extends ArtemisJpaRepository<CalendarSubscriptionTokenStore, Long> {

    @Query("""
                SELECT store.token
                FROM CalendarSubscriptionTokenStore store
                JOIN store.user jhiUser
                WHERE jhiUser.login = :login
            """)
    Optional<String> findTokenByUserLogin(@Param("login") String login);

    /**
     * Replaces the token of the user, which ends the validity of the previous token.
     *
     * @param login the login of the user
     * @param token the new token
     * @return the number of replaced tokens, which is 0 if the user has no token yet
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE CalendarSubscriptionTokenStore store
            SET store.token = :token
            WHERE store.user.id = (SELECT jhiUser.id FROM User jhiUser WHERE jhiUser.login = :login)
            """)
    int replaceTokenByUserLogin(@Param("login") String login, @Param("token") String token);
}
