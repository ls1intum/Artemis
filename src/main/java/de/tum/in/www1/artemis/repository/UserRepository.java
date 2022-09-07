package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.repository.specs.UserSpecs.*;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.web.rest.dto.UserPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String USERS_CACHE = "users";

    String FILTER_INTERNAL = "INTERNAL";

    String FILTER_EXTERNAL = "EXTERNAL";

    String FILTER_ACTIVATED = "ACTIVATED";

    String FILTER_DEACTIVATED = "DEACTIVATED";

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByActivationKey(String activationKey);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLoginAndIsInternal(String login, boolean isInternal);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIdIn(Set<Long> ids);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "organizations" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndOrganizationsById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "organizations" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndOrganizationsByLogin(String userLogin);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "guidedTourSettings" })
    Optional<User> findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(String login);

    @Query("select count(*) from User user where :#{#groupName} member of user.groups")
    Long countByGroupsIsContaining(@Param("groupName") String groupName);

    @Query("select user from User user where lower(user.email) = lower(:#{#searchInput}) or lower(user.login) = lower(:#{#searchInput})")
    List<User> findAllByEmailOrUsernameIgnoreCase(@Param("searchInput") String searchInput);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("select user from User user where :#{#groupName} member of user.groups")
    List<User> findAllInGroupWithAuthorities(@Param("groupName") String groupName);

    @Query("select user from User user where :#{#groupName} member of user.groups")
    List<User> findAllInGroup(@Param("groupName") String groupName);

    @Query("select user from User user where user.isInternal = :#{#isInternal}")
    List<User> findAllByInternal(boolean isInternal);

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
     * Search for all users by login or name in a group
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to DTOs
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("select user from User user where :#{#groupName} member of user.groups and "
            + "(user.login like :#{#loginOrName}% or concat_ws(' ', user.firstName, user.lastName) like %:#{#loginOrName}%)")
    Page<User> searchAllByLoginOrNameInGroup(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupName") String groupName);

    /**
     * Search for all users by login or name in a group and convert them to {@link UserDTO}
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to {@link UserDTO}
     */
    default Page<UserDTO> searchAllUsersByLoginOrNameInGroupAndConvertToDTO(Pageable pageable, String loginOrName, String groupName) {
        Page<User> users = searchAllByLoginOrNameInGroup(pageable, loginOrName, groupName);
        return users.map(UserDTO::new);
    }

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
     * @param groupName Name of group in which to search for users
     * @param logins    Logins of users
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
     * @return            list of found users that match the search criteria
     */
    @Query("select user from User user where user.login like :#{#loginOrName}% or concat_ws(' ', user.firstName, user.lastName) like %:#{#loginOrName}%")
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("select user from User user")
    Page<User> findAllWithGroups(Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("select user from User user")
    Set<User> findAllWithGroupsAndAuthorities();

    @Modifying
    @Transactional // ok because of modifying query
    @Query("Update User user set user.lastNotificationRead = :#{#lastNotificationRead} where user.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long userId, @Param("lastNotificationRead") ZonedDateTime lastNotificationRead);

    /**
     * Update user notification hide until property for current user
     * I.e. updates the filter that hides all notifications with a creation/notification date prior to the set value.
     * If the value is null then all notifications should be shown.
     * (Not to be confused with notification settings. This filter is based on the notification date alone)
     *
     * @param userId                of the user
     * @param hideNotificationUntil indicates a time that is used to filter all notifications that are prior to it
     *                              (if null -> show all notifications)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("Update User user set user.hideNotificationsUntil = :#{#hideNotificationUntil} where user.id = :#{#userId}")
    void updateUserNotificationVisibility(@Param("userId") Long userId, @Param("hideNotificationUntil") ZonedDateTime hideNotificationUntil);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("Update User user set user.langKey = :#{#languageKey} where user.id = :#{#userId}")
    void updateUserLanguageKey(@Param("userId") Long userId, @Param("languageKey") String languageKey);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("select user from User user where :#{#groupName} member of user.groups and user not in :#{#ignoredUsers}")
    List<User> findAllInGroupContainingAndNotIn(@Param("groupName") String groupName, @Param("ignoredUsers") Set<User> ignoredUsers);

    @Query("select distinct team.students from Team team where team.exercise.course.id = :#{#courseId} and team.shortName = :#{#teamShortName}")
    Set<User> findAllInTeam(@Param("courseId") Long courseId, @Param("teamShortName") String teamShortName);

    /**
     * Get all managed users
     *
     * @param userSearch used to find users
     * @return all users
     */
    default Page<UserDTO> getAllManagedUsers(UserPageableSearchDTO userSearch) {
        // Prepare filter
        final var searchTerm = userSearch.getSearchTerm();
        var sorting = Sort.by(userSearch.getSortedColumn());
        sorting = userSearch.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(userSearch.getPage(), userSearch.getPageSize(), sorting);

        // List of authorities that a user should match at least one
        Set<String> authorities = userSearch.getAuthorities();
        var modifiedAuthorities = authorities.stream().map(auth -> Role.ROLE_PREFIX + auth).collect(Collectors.toSet());

        // Internal or external users or both
        final var internal = userSearch.getOrigins().contains(FILTER_INTERNAL);
        final var external = userSearch.getOrigins().contains(FILTER_EXTERNAL);

        // Activated or deactivated users or both
        var activated = userSearch.getStatus().contains(FILTER_ACTIVATED);
        var deactivated = userSearch.getStatus().contains(FILTER_DEACTIVATED);

        // Course Ids
        var courseIds = userSearch.getCourseIds();

        Specification<User> specification = Specification.where(distinct()).and(getSearchTermSpecification(searchTerm)).and(getInternalOrExternalSpecification(internal, external))
                .and(getActivatedOrDeactivatedSpecification(activated, deactivated)).and(getAuthoritySpecification(modifiedAuthorities, courseIds))
                .and(getCourseSpecification(courseIds, modifiedAuthorities)).and(getAuthorityAndCourseSpecification(courseIds, modifiedAuthorities));

        return findAll(specification, sorted).map(user -> {
            user.setVisibleRegistrationNumber();
            return new UserDTO(user);
        });
    }

    /**
     * Search for all users by login or name
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @return all users matching search criteria
     */
    default Page<UserDTO> searchAllUsersByLoginOrName(Pageable pageable, String loginOrName) {
        Page<User> users = searchAllByLoginOrName(pageable, loginOrName);
        return users.map(UserDTO::new);
    }

    /**
     * @return existing user object by current user login
     */
    @NotNull
    default User getUser() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Retrieve a user by its login, or else throw exception
     *
     * @param login the login of the user to search
     * @return the user entity if it exists
     */
    @NotNull
    default User getUserByLoginElseThrow(String login) {
        return findOneByLogin(login).orElseThrow(() -> new EntityNotFoundException("User: " + login));
    }

    /**
     * Get user with user groups and authorities of currently logged-in user
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAndAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithGroupsAndAuthoritiesByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups, authorities and organizations of currently logged-in user
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAndAuthoritiesAndOrganizations() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithGroupsAndAuthoritiesAndOrganizationsByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups, authorities and guided tour settings of currently logged-in user
     * Note: this method should only be invoked if the guided tour settings are really needed
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAuthoritiesAndGuidedTourSettings() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    @NotNull
    private User unwrapOptionalUser(Optional<User> optionalUser, String currentUserLogin) {
        return optionalUser.orElseThrow(() -> new EntityNotFoundException("No user found with login: " + currentUserLogin));
    }

    private String getCurrentUserLogin() {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            return currentUserLogin.get();
        }
        throw new EntityNotFoundException("ERROR: No current user login found!");
    }

    /**
     * Get user with user groups and authorities with the username (i.e. user.getLogin() or principal.getName())
     *
     * @param username the username of the user who should be retrieved from the database
     * @return the user that belongs to the given principal with eagerly loaded groups and authorities
     */
    @NotNull
    default User getUserWithGroupsAndAuthorities(@NotNull String username) {
        Optional<User> user = findOneWithGroupsAndAuthoritiesByLogin(username);
        return unwrapOptionalUser(user, username);
    }

    /**
     * Finds a single user with groups and authorities using the registration number
     *
     * @param registrationNumber user registration number as string
     * @return the user with groups and authorities
     */
    default Optional<User> findUserWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber) {
        if (!StringUtils.hasText(registrationNumber)) {
            return Optional.empty();
        }
        return findOneWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
    }

    /**
     * Finds a single user with groups and authorities using the login name
     *
     * @param login user login string
     * @return the user with groups and authorities
     */
    default Optional<User> findUserWithGroupsAndAuthoritiesByLogin(String login) {
        if (!StringUtils.hasText(login)) {
            return Optional.empty();
        }
        return findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    @NotNull
    default User findByIdWithGroupsAndAuthoritiesElseThrow(long userId) {
        return findOneWithGroupsAndAuthoritiesById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * Find user with eagerly loaded groups, authorities and organizations by its id
     *
     * @param userId the id of the user to find
     * @return the user with groups, authorities and organizations if it exists, else throw exception
     */
    @NotNull
    default User findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(long userId) {
        return findOneWithGroupsAndAuthoritiesAndOrganizationsById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * Get students by given course
     *
     * @param course object
     * @return list of students for given course
     */
    default List<User> getStudents(Course course) {
        return findAllInGroupWithAuthorities(course.getStudentGroupName());
    }

    /**
     * Get tutors by given course
     *
     * @param course object
     * @return list of tutors for given course
     */
    default List<User> getTutors(Course course) {
        return findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName());
    }

    /**
     * Get editors by given course
     *
     * @param course object
     * @return list of editors for given course
     */
    default List<User> getEditors(Course course) {
        return findAllInGroupWithAuthorities(course.getEditorGroupName());
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return A list of all users that have the role of instructor in the course
     */
    default List<User> getInstructors(Course course) {
        return findAllInGroupWithAuthorities(course.getInstructorGroupName());
    }

    /**
     * Finds all users that are part of the specified group, but are not contained in the collection of excluded users
     *
     * @param groupName     The group by which all users should get filtered
     * @param excludedUsers The users that should get ignored/excluded
     * @return A list of filtered users
     */
    default List<User> findAllUserInGroupAndNotIn(String groupName, Collection<User> excludedUsers) {
        // For an empty list, we have to use another query, because Hibernate builds an invalid query with empty lists
        if (!excludedUsers.isEmpty()) {
            return findAllInGroupContainingAndNotIn(groupName, new HashSet<>(excludedUsers));
        }
        return findAllInGroupWithAuthorities(groupName);
    }

    default Long countUserInGroup(String groupName) {
        return countByGroupsIsContaining(groupName);
    }

    /**
     * Update user notification read date for current user
     *
     * @param userId the user for which the notification read date should be updated
     */
    default void updateUserNotificationReadDate(long userId) {
        updateUserNotificationReadDate(userId, ZonedDateTime.now());
    }

    @Query(value = "SELECT * from jhi_user u where u.email regexp ?1", nativeQuery = true)
    List<User> findAllMatchingEmailPattern(@Param("emailPattern") String emailPattern);

    /**
     * Add organization to user, if not contained already
     *
     * @param userId       the id of the user to add to the organization
     * @param organization the organization to add to the user
     */
    @NotNull
    default void addOrganizationToUser(Long userId, Organization organization) {
        User user = findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(userId);
        if (!user.getOrganizations().contains(organization)) {
            user.getOrganizations().add(organization);
            save(user);
        }
    }

    /**
     * Remove organization from user, if currently contained
     *
     * @param userId       the id of the user to remove from the organization
     * @param organization the organization to remove from the user
     */
    @NotNull
    default void removeOrganizationFromUser(Long userId, Organization organization) {
        User user = findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(userId);
        if (user.getOrganizations().contains(organization)) {
            user.getOrganizations().remove(organization);
            save(user);
        }
    }

    /**
     * Return true if the current users' login matches the provided login
     * @param login user login
     * @return true if both logins match
     */
    default boolean isCurrentUser(String login) {
        var currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isEmpty()) {
            return false;
        }
        return currentUserLogin.get().equals(login);
    }
}
