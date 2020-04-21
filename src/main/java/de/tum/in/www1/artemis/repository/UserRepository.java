package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

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

    Optional<User> findByActivationKey(String activationKey);

    List<User> findAllByRegistrationNumberIsNull();

    Optional<User> findByResetKey(String resetKey);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findWithGroupsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findWithAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findWithGroupsAndAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "guidedTourSettings" })
    Optional<User> findWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(String login);

    @Query("SELECT COUNT(*) FROM User user WHERE :#{#groupName} MEMBER OF user.groups")
    Long countByGroupsIsContaining(@Param("groupName") String groupName);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user WHERE :#{#groupName} MEMBER OF user.groups")
    List<User> findAllInGroup(@Param("groupName") String groupName);

    /**
     * Searches for users in a group by their login or full name.
     * @param groupName Name of group in which to search for users
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user WHERE :#{#groupName} MEMBER OF user.groups AND "
            + "(user.login LIKE :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%)")
    List<User> searchByLoginOrNameInGroup(@Param("groupName") String groupName, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users by their login or full name.
     * @param page Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user where user.login like :#{#loginOrName}% or concat_ws(' ', user.firstName, user.lastName) like %:#{#loginOrName}%")
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user")
    Page<User> findAllWithGroups(Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user WHERE user.login LIKE %:#{#searchTerm}% OR user.email LIKE %:#{#searchTerm}% "
            + "OR user.lastName LIKE %:#{#searchTerm}% OR user.firstName LIKE %:#{#searchTerm}%")
    Page<User> searchAllByLoginOrNameWithGroups(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Modifying
    @Query("UPDATE User user SET user.lastNotificationRead = current_timestamp() WHERE user.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long userId);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user WHERE :#{#groupName} MEMBER OF user.groups AND user NOT IN :#{#ignoredUsers}")
    List<User> findAllInGroupContainingAndNotIn(@Param("groupName") String groupName, @Param("ignoredUsers") Set<User> ignoredUsers);
}
