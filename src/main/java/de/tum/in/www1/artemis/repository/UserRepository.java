package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

/**
 * Spring Data JPA repository for the User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    String USERS_CACHE = "users";

    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByRegistrationNumberIsNull();

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "guidedTourSettings" })
    Optional<User> findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(String login);

    @Query("select count(*) from User user where :#{#groupName} member of user.groups")
    Long countByGroupsIsContaining(@Param("groupName") String groupName);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("select user from User user where :#{#groupName} member of user.groups")
    List<User> findAllInGroupWithAuthorities(@Param("groupName") String groupName);

    /**
     * Searches for users in a group by their login or full name.
     *
     * @param groupName   Name of group in which to search for users
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("select user from User user where :#{#groupName} member of user.groups and "
            + "(user.login like :#{#loginOrName}% or concat_ws(' ', user.firstName, user.lastName) like %:#{#loginOrName}%)")
    List<User> searchByLoginOrNameInGroup(@Param("groupName") String groupName, @Param("loginOrName") String loginOrName);

    /**
     * Gets users in a group by their registration number.
     *
     * @param groupName           Name of group in which to search for users
     * @param registrationNumbers Registration numbers of users
     * @return found users that match the criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            select user
            from User user
            where :#{#groupName} member of user.groups and user.registrationNumber in :#{#registrationNumbers}
            """)
    List<User> findAllByRegistrationNumbersInGroup(@Param("groupName") String groupName, @Param("registrationNumbers") Set<String> registrationNumbers);

    /**
     * Gets users in a group by their login.
     *
     * @param groupName           Name of group in which to search for users
     * @param logins Logins of users
     * @return found users that match the criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            select user
            from User user
            where :#{#groupName} member of user.groups and user.login in :#{#logins}
            """)
    List<User> findAllByLoginsInGroup(@Param("groupName") String groupName, @Param("logins") Set<String> logins);

    /**
     * Searches for users by their login or full name.
     *
     * @param page        Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("select user from User user where user.login like :#{#loginOrName}% or concat_ws(' ', user.firstName, user.lastName) like %:#{#loginOrName}%")
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("select user from User user")
    Page<User> findAllWithGroups(Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("select user from User user where user.login like %:#{#searchTerm}% or user.email like %:#{#searchTerm}% "
            + "or user.lastName like %:#{#searchTerm}% or user.firstName like %:#{#searchTerm}%")
    Page<User> searchByLoginOrNameWithGroups(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Modifying
    @Query("Update User user set user.lastNotificationRead = :#{#lastNotificationRead} where user.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long userId, @Param("lastNotificationRead") ZonedDateTime lastNotificationRead);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("select user from User user where :#{#groupName} member of user.groups and user not in :#{#ignoredUsers}")
    List<User> findAllInGroupContainingAndNotIn(@Param("groupName") String groupName, @Param("ignoredUsers") Set<User> ignoredUsers);

    @Query("select distinct team.students from Team team where team.exercise.course.id = :#{#courseId} and team.shortName = :#{#teamShortName}")
    Set<User> findAllInTeam(@Param("courseId") Long courseId, @Param("teamShortName") String teamShortName);
}
