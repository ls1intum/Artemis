package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
