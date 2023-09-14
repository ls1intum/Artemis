package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.repository.specs.UserSpecs.*;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.web.rest.dto.UserPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.isDeleted = false` to your query.
 * </p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String USERS_CACHE = "users";

    String FILTER_INTERNAL = "INTERNAL";

    String FILTER_EXTERNAL = "EXTERNAL";

    String FILTER_ACTIVATED = "ACTIVATED";

    String FILTER_DEACTIVATED = "DEACTIVATED";

    String FILTER_WITH_REG_NO = "WITH_REG_NO";

    String FILTER_WITHOUT_REG_NO = "WITHOUT_REG_NO";

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

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByEmail(String email);

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

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findOneWithLearningPathsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findWithLearningPathsById(long userId);

    @Query("SELECT count(*) FROM User user WHERE user.isDeleted = false AND :#{#groupName} MEMBER OF user.groups")
    Long countByGroupsIsContaining(@Param("groupName") String groupName);

    @Query("""
            SELECT user FROM User user WHERE user.isDeleted = false
            AND (lower(user.email) = lower(:#{#searchInput})
            OR lower(user.login) = lower(:#{#searchInput}))
            """)
    List<User> findAllByEmailOrUsernameIgnoreCase(@Param("searchInput") String searchInput);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("SELECT user FROM User user WHERE user.isDeleted = false AND :#{#groupName} MEMBER OF user.groups")
    Set<User> findAllInGroupWithAuthorities(@Param("groupName") String groupName);

    @Query("SELECT user FROM User user WHERE user.isDeleted = false AND :#{#groupName} MEMBER OF user.groups")
    Set<User> findAllInGroup(@Param("groupName") String groupName);

    @Query("""
            SELECT NEW de.tum.in.www1.artemis.domain.ConversationWebSocketRecipientSummary (
                user,
                CASE WHEN cp.isHidden = true THEN true ELSE false END,
                CASE WHEN atLeastTutors.id IS NOT null THEN true ELSE false END
            )
            FROM User user
            JOIN UserGroup ug ON ug.userId = user.id
            LEFT JOIN Course students ON ug.group = students.studentGroupName
            LEFT JOIN Course atLeastTutors ON (atLeastTutors.teachingAssistantGroupName = ug.group
                OR atLeastTutors.editorGroupName = ug.group
                OR atLeastTutors.instructorGroupName = ug.group
            )
            LEFT JOIN ConversationParticipant cp ON cp.user.id = user.id AND cp.conversation.id = :conversationId
            WHERE user.isDeleted = false
            AND (students.id = :courseId OR atLeastTutors.id = :courseId)
            """)
    Set<ConversationWebSocketRecipientSummary> findAllWebSocketRecipientsInCourseForConversation(@Param("courseId") Long courseId, @Param("conversationId") Long conversationId);

    /**
     * Searches for users in a group by their login or full name.
     *
     * @param groupName   Name of group in which to search for users
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            SELECT user FROM User user
            WHERE user.isDeleted = false AND (:#{#groupName} MEMBER OF user.groups
            AND (user.login LIKE :#{#loginOrName}%
            OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%))
            """)
    List<User> searchByLoginOrNameInGroup(@Param("groupName") String groupName, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users in groups by their full name.
     *
     * @param groupNames List of names of groups in which to search for users
     * @param nameOfUser Name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
             SELECT user FROM User user
             LEFT JOIN user.groups userGroup
             WHERE user.isDeleted = false AND (
                userGroup IN :#{#groupNames}
                AND concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#nameOfUser}%
             )
             ORDER BY concat_ws(' ', user.firstName, user.lastName)
            """)
    List<User> searchByNameInGroups(@Param("groupNames") Set<String> groupNames, @Param("nameOfUser") String nameOfUser);

    /**
     * Search for all users by login or name in a group
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to DTOs
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            SELECT user FROM User user WHERE user.isDeleted = false
            AND (:#{#groupName} MEMBER OF user.groups AND (user.login LIKE :#{#loginOrName}%
            OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%))
            """)
    Page<User> searchAllByLoginOrNameInGroup(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupName") String groupName);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
             SELECT user
             FROM User user
             LEFT JOIN user.groups userGroup
             WHERE user.isDeleted = false AND (
                userGroup IN :#{#groupNames}
                AND (user.login LIKE :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%)
                AND user.id <> :#{#idOfUser}
             )
             ORDER BY concat_ws(' ', user.firstName, user.lastName)
            """)
    Page<User> searchAllByLoginOrNameInGroupsNotUserId(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames,
            @Param("idOfUser") Long idOfUser);

    /**
     * Search for all users by login or name within the provided groups
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupNames  Names of groups in which to search for users
     * @return All users matching search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
             SELECT user
             FROM User user
             LEFT JOIN user.groups userGroup
             WHERE user.isDeleted = false AND (
                userGroup IN :#{#groupNames}
                AND (user.login LIKE :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%)
             )
            """)
    Page<User> searchAllByLoginOrNameInGroups(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
             SELECT DISTINCT user
             FROM User user
             JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
             JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
             WHERE user.isDeleted = false AND (
                conversation.id = :#{#conversationId}
                AND (:#{#loginOrName} = '' OR (user.login LIKE :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%))
             )
            """)
    Page<User> searchAllByLoginOrNameInConversation(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("conversationId") Long conversationId);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
             SELECT DISTINCT user
             FROM User user
             JOIN user.groups userGroup
             JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
             JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
             WHERE user.isDeleted = false AND (
                conversation.id = :#{#conversationId}
                AND (:#{#loginOrName} = '' OR (user.login LIKE :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%))
                AND userGroup IN :#{#groupNames}
             )
            """)
    Page<User> searchAllByLoginOrNameInConversationWithCourseGroups(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("conversationId") Long conversationId,
            @Param("groupNames") Set<String> groupNames);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            SELECT DISTINCT user
            FROM User user
            JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
            JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = false
            AND (
                conversation.id = :#{#conversationId}
                AND (:#{#loginOrName} = '' OR (user.login LIKE :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%))
                AND conversationParticipant.isModerator = true
                )
            """)
    Page<User> searchChannelModeratorsByLoginOrNameInConversation(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("conversationId") Long conversationId);

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
            SELECT user
            FROM User user
            WHERE user.isDeleted = false AND :#{#groupName} MEMBER OF user.groups AND user.registrationNumber IN :#{#registrationNumbers}
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
            SELECT user
            FROM User user
            WHERE user.isDeleted = false AND :#{#groupName} MEMBER OF user.groups AND user.login IN :#{#logins}
            """)
    List<User> findAllByLoginsInGroup(@Param("groupName") String groupName, @Param("logins") Set<String> logins);

    /**
     * Searches for users by their login or full name.
     *
     * @param page        Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT user FROM User user WHERE user.isDeleted = false
            AND (user.login like :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%)
            """)
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users in a course by their login or full name.
     *
     * @param page        Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @param courseId    Id of the course the user has to be a member of
     * @return list of found users that match the search criteria
     */
    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            SELECT user FROM User user
            JOIN Course course ON course.id = :#{#courseId}
            WHERE user.isDeleted = false
            AND (user.login like :#{#loginOrName}% OR concat_ws(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%)
            AND (course.studentGroupName MEMBER OF user.groups
                OR course.teachingAssistantGroupName MEMBER OF user.groups
                OR course.editorGroupName MEMBER OF user.groups
                OR course.instructorGroupName MEMBER OF user.groups
            )
            """)
    Page<User> searchAllByLoginOrNameInCourse(Pageable page, @Param("loginOrName") String loginOrName, @Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user WHERE user.isDeleted = false")
    Page<User> findAllWithGroups(Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    @Query("SELECT user FROM User user WHERE user.isDeleted = false")
    Set<User> findAllWithGroupsAndAuthorities();

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE User user SET user.lastNotificationRead = :#{#lastNotificationRead} WHERE user.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long userId, @Param("lastNotificationRead") ZonedDateTime lastNotificationRead);

    /**
     * Update user notification hide until property for current user
     * I.e. updates the filter that hides all notifications with a creation/notification date prior to the set value.
     * If the value is null then all notifications should be shown.
     * (Not to be confused with notification settings. This filter is based on the notification date alone)
     *
     * @param userId                of the user
     * @param hideNotificationUntil indicates a time that is used to filter all notifications that are prior to it
     *                                  (if null -> show all notifications)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE User user SET user.hideNotificationsUntil = :#{#hideNotificationUntil} WHERE user.id = :#{#userId}")
    void updateUserNotificationVisibility(@Param("userId") Long userId, @Param("hideNotificationUntil") ZonedDateTime hideNotificationUntil);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("UPDATE User user SET user.langKey = :#{#languageKey} WHERE user.id = :#{#userId}")
    void updateUserLanguageKey(@Param("userId") Long userId, @Param("languageKey") String languageKey);

    @Modifying
    @Transactional
    @Query("UPDATE User user SET user.irisAccepted = :#{#acceptDatetime} WHERE user.id = :#{#userId}")
    void updateIrisAcceptedToDate(@Param("userId") Long userId, @Param("acceptDatetime") ZonedDateTime acceptDatetime);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("SELECT user FROM User user WHERE user.isDeleted = false AND :#{#groupName} MEMBER OF user.groups AND user NOT IN :#{#ignoredUsers}")
    Set<User> findAllInGroupContainingAndNotIn(@Param("groupName") String groupName, @Param("ignoredUsers") Set<User> ignoredUsers);

    @Query("""
            SELECT DISTINCT team.students AS student
            FROM Team team
            JOIN team.students st
            WHERE st.isDeleted = false
            AND team.exercise.course.id = :#{#courseId} AND team.shortName = :#{#teamShortName}
            """)
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

        // Users without registration numbers or with registration numbers
        var noRegistrationNumber = userSearch.getRegistrationNumbers().contains(FILTER_WITHOUT_REG_NO);
        var withRegistrationNumber = userSearch.getRegistrationNumbers().contains(FILTER_WITH_REG_NO);

        Specification<User> specification = Specification.where(distinct()).and(notSoftDeleted()).and(getSearchTermSpecification(searchTerm))
                .and(getInternalOrExternalSpecification(internal, external)).and(getActivatedOrDeactivatedSpecification(activated, deactivated))
                .and(getAuthoritySpecification(modifiedAuthorities, courseIds)).and(getCourseSpecification(courseIds, modifiedAuthorities))
                .and(getAuthorityAndCourseSpecification(courseIds, modifiedAuthorities))
                .and(getWithOrWithoutRegistrationNumberSpecification(noRegistrationNumber, withRegistrationNumber));

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

    /**
     * Finds a single user with groups and authorities using the email
     *
     * @param email user email string
     * @return the user with groups and authorities
     */
    default Optional<User> findUserWithGroupsAndAuthoritiesByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return findOneWithGroupsAndAuthoritiesByEmail(email);
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
     * Find user with eagerly loaded learning paths by its id
     *
     * @param userId the id of the user to find
     * @return the user with learning paths if it exists, else throw exception
     */
    @NotNull
    default User findWithLearningPathsByIdElseThrow(long userId) {
        return findWithLearningPathsById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * Get students by given course
     *
     * @param course object
     * @return students for given course
     */
    default Set<User> getStudents(Course course) {
        return findAllInGroupWithAuthorities(course.getStudentGroupName());
    }

    /**
     * Get tutors by given course
     *
     * @param course object
     * @return tutors for given course
     */
    default Set<User> getTutors(Course course) {
        return findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName());
    }

    /**
     * Get editors by given course
     *
     * @param course object
     * @return editors for given course
     */
    default Set<User> getEditors(Course course) {
        return findAllInGroupWithAuthorities(course.getEditorGroupName());
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return instructors for the given course
     */
    default Set<User> getInstructors(Course course) {
        return findAllInGroupWithAuthorities(course.getInstructorGroupName());
    }

    /**
     * Finds all users that are part of the specified group, but are not contained in the collection of excluded users
     *
     * @param groupName     The group by which all users should get filtered
     * @param excludedUsers The users that should get ignored/excluded
     * @return users who are in the given group except the excluded ones
     */
    default Set<User> findAllUserInGroupAndNotIn(String groupName, Collection<User> excludedUsers) {
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

    @Query(value = "SELECT * FROM jhi_user u WHERE is_deleted = false AND REGEXP_LIKE(u.email, :#{#emailPattern})", nativeQuery = true)
    List<User> findAllMatchingEmailPattern(@Param("emailPattern") String emailPattern);

    /**
     * Add organization to user, if not contained already
     *
     * @param userId       the id of the user to add to the organization
     * @param organization the organization to add to the user
     */
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
    default void removeOrganizationFromUser(Long userId, Organization organization) {
        User user = findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(userId);
        if (user.getOrganizations().contains(organization)) {
            user.getOrganizations().remove(organization);
            save(user);
        }
    }

    /**
     * Return true if the current users' login matches the provided login
     *
     * @param login user login
     * @return true if both logins match
     */
    default boolean isCurrentUser(String login) {
        return SecurityUtils.getCurrentUserLogin().map(currentLogin -> currentLogin.equals(login)).orElse(false);
    }

    default User findByIdElseThrow(long userId) throws EntityNotFoundException {
        return findById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }
}
