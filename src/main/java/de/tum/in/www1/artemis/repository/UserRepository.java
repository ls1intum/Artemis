package de.tum.in.www1.artemis.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;

/** Spring Data JPA repository for the User entity. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    String USERS_CACHE = "users";

    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByActivatedIsFalseAndCreatedDateBefore(Instant dateTime);

    List<User> findAllByRegistrationNumberIsNull();

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @Query("select distinct user from User user left join fetch user.groups left join fetch user.authorities where user.login = :#{#login}")
    Optional<User> findOneWithGroupsAndAuthoritiesByLogin(@Param("login") String login);

    @Query("select distinct user from User user left join fetch user.groups left join fetch user.authorities left join fetch user.guidedTourSettings where user.login = :#{#login}")
    Optional<User> findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(@Param("login") String login);

    @Query("select distinct user from User user left join fetch user.groups where user.login = :#{#login}")
    Optional<User> findOneWithGroupsByLogin(@Param("login") String login);

    @Query("select distinct user from User user left join fetch user.authorities where user.login = :#{#login}")
    @Cacheable(cacheNames = USERS_CACHE)
    Optional<User> findOneWithAuthoritiesByLogin(@Param("login") String login);

    Long countByGroupsIsContaining(Set<String> groups);

    List<User> findAllByGroups(String group);

    @Modifying
    @Query("Update User user set user.lastNotificationRead = utc_timestamp where user.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long userId);
}
