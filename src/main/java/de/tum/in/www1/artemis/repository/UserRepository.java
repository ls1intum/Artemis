package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    List<User> findAllByRegistrationNumberIsNull();

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByLogin(String login);

    @EntityGraph(attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLogin(String login);

    @EntityGraph(attributePaths = { "groups", "authorities", "guidedTourSettings" })
    Optional<User> findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(String login);

    Long countByGroupsIsContaining(String group);

    @EntityGraph(attributePaths = { "groups" })
    @Query("select user from User user where :#{#groupName} member user.groups")
    List<User> findAllInGroup(String groupName);

    @EntityGraph(attributePaths = { "groups" })
    @Query("select user from User user")
    Page<User> findAllWithGroups(Pageable pageable);

    @Modifying
    @Query("Update User user set user.lastNotificationRead = utc_timestamp where user.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long userId);

    @EntityGraph(attributePaths = { "groups" })
    @Query("select user from User user where :#{#groupName} member user.groups and user not in :#{#ignoredUsers}")
    List<User> findAllInGroupContainingAndNotIn(String groupName, Set<User> ignoredUsers);
}
