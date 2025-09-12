package de.tum.cit.aet.artemis.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

import de.tum.cit.aet.artemis.core.domain.CalendarSubscriptionTokenStore;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

public interface CalendarSubscriptionTokenStoreRepository extends ArtemisJpaRepository<CalendarSubscriptionTokenStore, Long> {

    @Query("""
                SELECT store.token
                FROM CalendarSubscriptionTokenStore store
                JOIN store.user jhiUser
                WHERE jhiUser.login = :login
            """)
    Optional<String> findTokenByUserLogin(String login);
}
