package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.distinct;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.getActivatedOrDeactivatedSpecification;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.getAllUsersWithoutUserGroups;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.getAuthoritySpecification;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.getInternalOrExternalSpecification;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.getSearchTermSpecification;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.getWithOrWithoutRegistrationNumberSpecification;
import static de.tum.cit.aet.artemis.repository.specs.UserSpecs.notSoftDeleted;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.Organization;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.SortingOrder;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.SecurityUtils;
import de.tum.cit.aet.artemis.service.dto.UserDTO;
import de.tum.cit.aet.artemis.service.dto.UserRoleDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.UserPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.isDeleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public interface UserRepository extends ArtemisJpaRepository<User, Long>, JpaSpecificationExecutor<User> {

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
    Optional<User> findOneWithGroupsAndAuthoritiesByEmailAndIsInternal(String email, boolean isInternal);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesById(Long id);

    /**
     * Retrieves a list of user roles within a specified course based on the provided user IDs. This method is highly optimized for performance.
     *
     * <p>
     * This query method creates a list of {@link UserRoleDTO} objects containing the user ID,
     * user login, and assigned role (INSTRUCTOR, TUTOR, or USER) for each user in the specified course. The role is determined
     * based on the user's authorities and group memberships.
     * </p>
     *
     * <p>
     * The role assignment follows this precedence:
     * <ul>
     * <li>If the user has the ADMIN authority, they are assigned the role 'INSTRUCTOR'.</li>
     * <li>If the user belongs to the course's instructor group, they are assigned the role 'INSTRUCTOR'.</li>
     * <li>If the user belongs to the course's editor group or teaching assistant group, they are assigned the role 'TUTOR'.</li>
     * <li>If the user belongs to the course's student group, they are assigned the role 'USER'.</li>
     * </ul>
     * </p>
     *
     * @param userIds  a collection of user IDs for which the roles are to be fetched
     * @param courseId the ID of the course for which the user roles are to be determined
     * @return a list of {@link UserRoleDTO} objects containing the user ID, user login, and role for each user
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.service.dto.UserRoleDTO(user.id, user.login,
                   CASE
                       WHEN :#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities THEN 'INSTRUCTOR'
                       WHEN course.instructorGroupName MEMBER OF user.groups THEN 'INSTRUCTOR'
                       WHEN course.editorGroupName MEMBER OF user.groups THEN 'TUTOR'
                       WHEN course.teachingAssistantGroupName MEMBER OF user.groups THEN 'TUTOR'
                       WHEN course.studentGroupName MEMBER OF user.groups THEN 'USER'
                   END)
            FROM User user
            INNER JOIN Course course
            ON course.id = :courseId
            WHERE user.id IN :userIds
            """)
    List<UserRoleDTO> findUserRolesInCourse(@Param("userIds") Collection<Long> userIds, @Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "organizations" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndOrganizationsById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "organizations" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndOrganizationsByLogin(String userLogin);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "guidedTourSettings" })
    Optional<User> findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "guidedTourSettings", "irisAccepted" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndGuidedTourSettingsAndIrisAcceptedTimestampByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findOneWithLearningPathsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findWithLearningPathsById(long userId);

    Long countByIsDeletedIsFalseAndGroupsContains(String groupName);

    @Query("""
            SELECT DISTINCT user
            FROM User user
            WHERE user.isDeleted = FALSE
                AND (
                    LOWER(user.email) = LOWER(:searchInput)
                    OR LOWER(user.login) = LOWER(:searchInput)
                )
            """)
    List<User> findAllByEmailOrUsernameIgnoreCase(@Param("searchInput") String searchInput);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(String groupName);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups userGroup
                LEFT JOIN FETCH user.authorities userAuthority
            WHERE user.isDeleted = FALSE
                AND userGroup IN :groupNames
            """)
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(@Param("groupNames") Set<String> groupNames);

    Set<User> findAllByIsDeletedIsFalseAndGroupsContains(String groupName);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.ConversationNotificationRecipientSummary (
                user.id,
                user.login,
                user.firstName,
                user.lastName,
                user.langKey,
                user.email,
                CASE WHEN cp.isMuted = TRUE THEN TRUE ELSE FALSE END,
                CASE WHEN cp.isHidden = TRUE THEN TRUE ELSE FALSE END,
                CASE WHEN ug.group = :teachingAssistantGroupName
                    OR ug.group = :editorGroupName
                    OR ug.group = :instructorGroupName
                THEN TRUE ELSE FALSE END
            )
            FROM User user
                JOIN UserGroup ug ON ug.userId = user.id
                LEFT JOIN ConversationParticipant cp ON cp.user = user AND cp.conversation.id = :conversationId
            WHERE user.isDeleted = FALSE
                AND (
                    ug.group = :studentGroupName
                    OR ug.group = :teachingAssistantGroupName
                    OR ug.group = :editorGroupName
                    OR ug.group = :instructorGroupName
                )
            """)
    Set<ConversationNotificationRecipientSummary> findAllNotificationRecipientsInCourseForConversation(@Param("conversationId") long conversationId,
            @Param("studentGroupName") String studentGroupName, @Param("teachingAssistantGroupName") String teachingAssistantGroupName,
            @Param("editorGroupName") String editorGroupName, @Param("instructorGroupName") String instructorGroupName);

    /**
     * Searches for users in a group by their login or full name.
     *
     * @param groupName   Name of group in which to search for users
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND :groupName = userGroup
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
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
            SELECT user
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND (
                    userGroup IN :groupNames
                    AND CONCAT(user.firstName, ' ', user.lastName) LIKE %:nameOfUser%
                 )
            ORDER BY CONCAT(user.firstName, ' ', user.lastName)
            """)
    List<User> searchByNameInGroups(@Param("groupNames") Set<String> groupNames, @Param("nameOfUser") String nameOfUser);

    @Query("""
            SELECT user.id
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND :groupName = userGroup
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                )
            """)
    List<Long> findUserIdsByLoginOrNameInGroup(@Param("loginOrName") String loginOrName, @Param("groupName") String groupName, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "groups")
    List<User> findUsersWithGroupsByIdIn(List<Long> ids);

    @Query("""
            SELECT COUNT(user)
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND :groupName = userGroup
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                )
            """)
    long countUsersByLoginOrNameInGroup(@Param("loginOrName") String loginOrName, @Param("groupName") String groupName);

    /**
     * Search for all users by login or name in a group
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to DTOs
     */
    default Page<User> searchAllWithGroupsByLoginOrNameInGroup(Pageable pageable, String loginOrName, String groupName) {
        List<Long> ids = findUserIdsByLoginOrNameInGroup(loginOrName, groupName, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersWithGroupsByIdIn(ids);
        return new PageImpl<>(users, pageable, countUsersByLoginOrNameInGroup(loginOrName, groupName));
    }

    @Query("""
            SELECT user.id
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                ) AND user.id <> :idOfUser
            """)
    List<Long> findUserIdsByLoginOrNameInGroupsNotUserId(@Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames, @Param("idOfUser") long idOfUser,
            Pageable pageable);

    @Query("""
            SELECT user
            FROM User user
                LEFT JOIN FETCH user.groups userGroup
            WHERE user.id IN :ids
            ORDER BY CONCAT(user.firstName, ' ', user.lastName)
            """)
    List<User> findUsersByIdsWithGroupsOrdered(@Param("ids") List<Long> ids);

    @Query("""
            SELECT COUNT(user)
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                ) AND user.id <> :idOfUser
            """)
    long countUsersByLoginOrNameInGroupsNotUserId(@Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames, @Param("idOfUser") long idOfUser);

    /**
     * Searches for {@link User} entities by login or name within specified groups, excluding a specific user ID.
     * The results are paginated.
     *
     * @param pageable    the pagination information.
     * @param loginOrName the login or name to search for.
     * @param groupNames  the set of group names to limit the search within.
     * @param idOfUser    the ID of the user to exclude from the search results.
     * @return a paginated list of {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchAllWithGroupsByLoginOrNameInGroupsNotUserId(Pageable pageable, String loginOrName, Set<String> groupNames, long idOfUser) {
        List<Long> ids = findUserIdsByLoginOrNameInGroupsNotUserId(loginOrName, groupNames, idOfUser, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithGroupsOrdered(ids);
        return new PageImpl<>(users, pageable, countUsersByLoginOrNameInGroupsNotUserId(loginOrName, groupNames, idOfUser));
    }

    @Query("""
            SELECT user.id
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    List<Long> findUserIdsByLoginOrNameInGroups(@Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames, Pageable pageable);

    @Query("""
            SELECT COUNT(user)
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    long countUsersByLoginOrNameInGroups(@Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames);

    /**
     * Search for all users by login or name within the provided groups
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupNames  Names of groups in which to search for users
     * @return All users matching search criteria
     */
    default Page<User> searchAllWithGroupsByLoginOrNameInGroups(Pageable pageable, String loginOrName, Set<String> groupNames) {
        List<Long> ids = findUserIdsByLoginOrNameInGroups(loginOrName, groupNames, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersWithGroupsByIdIn(ids);
        return new PageImpl<>(users, pageable, countUsersByLoginOrNameInGroups(loginOrName, groupNames));
    }

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    List<User> findUsersByLoginOrNameInConversation(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    long countUsersByLoginOrNameInConversation(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId);

    /**
     * Searches for {@link User} entities by login or name within a specific conversation.
     * The results are paginated.
     *
     * @param pageable       the pagination information.
     * @param loginOrName    the login or name to search for.
     * @param conversationId the ID of the conversation to limit the search within.
     * @return a paginated list of {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchAllWithGroupsByLoginOrNameInConversation(Pageable pageable, String loginOrName, long conversationId) {
        List<Long> ids = findUsersByLoginOrNameInConversation(loginOrName, conversationId, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersWithGroupsByIdIn(ids);
        long total = countUsersByLoginOrNameInConversation(loginOrName, conversationId);
        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                ) AND userGroup IN :groupNames
            """)
    List<User> findUsersByLoginOrNameInConversationWithCourseGroups(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId,
            @Param("groupNames") Set<String> groupNames, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                ) AND userGroup IN :groupNames
            """)
    long countUsersByLoginOrNameInConversationWithCourseGroups(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId,
            @Param("groupNames") Set<String> groupNames);

    /**
     * Searches for {@link User} entities by login or name within a specific conversation and course groups.
     * The results are paginated.
     *
     * @param pageable       the pagination information.
     * @param loginOrName    the login or name to search for.
     * @param conversationId the ID of the conversation to limit the search within.
     * @param groupNames     the set of course group names to limit the search within.
     * @return a paginated list of {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchAllWithCourseGroupsByLoginOrNameInConversation(Pageable pageable, String loginOrName, long conversationId, Set<String> groupNames) {
        List<Long> ids = findUsersByLoginOrNameInConversationWithCourseGroups(loginOrName, conversationId, groupNames, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersWithGroupsByIdIn(ids);
        long total = countUsersByLoginOrNameInConversationWithCourseGroups(loginOrName, conversationId, groupNames);
        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                ) AND conversationParticipant.isModerator = TRUE
            """)
    List<User> findModeratorsByLoginOrNameInConversation(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted = FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                ) AND conversationParticipant.isModerator = TRUE
            """)
    long countModeratorsByLoginOrNameInConversation(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId);

    /**
     * Searches for channel moderator {@link User} entities by login or name within a specific conversation.
     * The results are paginated.
     *
     * @param pageable       the pagination information.
     * @param loginOrName    the login or name to search for.
     * @param conversationId the ID of the conversation to limit the search within.
     * @return a paginated list of channel moderator {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchChannelModeratorsWithGroupsByLoginOrNameInConversation(Pageable pageable, String loginOrName, long conversationId) {
        List<Long> ids = findModeratorsByLoginOrNameInConversation(loginOrName, conversationId, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findDistinctUsersWithGroupsByIdIn(ids); // these users are moderators
        long total = countModeratorsByLoginOrNameInConversation(loginOrName, conversationId);
        return new PageImpl<>(users, pageable, total);
    }

    /**
     * Search for all users by login or name in a group and convert them to {@link UserDTO}
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to {@link UserDTO}
     */
    default Page<UserDTO> searchAllUsersByLoginOrNameInGroupAndConvertToDTO(Pageable pageable, String loginOrName, String groupName) {
        Page<User> users = searchAllWithGroupsByLoginOrNameInGroup(pageable, loginOrName, groupName);
        return users.map(UserDTO::new);
    }

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    List<User> findAllWithGroupsByIsDeletedIsFalseAndGroupsContainsAndRegistrationNumberIn(String groupName, Set<String> registrationNumbers);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    List<User> findAllWithGroupsByIsDeletedIsFalseAndGroupsContainsAndLoginIn(String groupName, Set<String> logins);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndLoginIn(Set<String> logins);

    /**
     * Searches for users by their login or full name.
     *
     * @param page        Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT user
            FROM User user
            WHERE user.isDeleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.groups userGroup
                JOIN Course course ON course.id = :courseId
            WHERE user.isDeleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
                AND (course.studentGroupName = userGroup
                    OR course.teachingAssistantGroupName = userGroup
                    OR course.editorGroupName = userGroup
                    OR course.instructorGroupName = userGroup
               )
            """)
    List<User> findUsersByLoginOrNameInCourse(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "groups")
    List<User> findDistinctUsersWithGroupsByIdIn(List<Long> ids);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN user.groups userGroup
                JOIN Course course ON course.id = :courseId
            WHERE user.isDeleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
                AND (course.studentGroupName = userGroup
                    OR course.teachingAssistantGroupName = userGroup
                    OR course.editorGroupName = userGroup
                    OR course.instructorGroupName = userGroup
                )
            """)
    long countUsersByLoginOrNameInCourse(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId);

    /**
     * Searches for users by login or name within a course and returns a list of distinct users along with their groups.
     * This method avoids in-memory paging by retrieving the user IDs directly from the database.
     *
     * @param pageable    the pagination information
     * @param loginOrName the login or name of the users to search for
     * @param courseId    the ID of the course to search within
     * @return a list of distinct users with their groups, or an empty list if no users are found
     */
    default List<User> searchAllWithGroupsByLoginOrNameInCourseAndReturnList(Pageable pageable, String loginOrName, long courseId) {
        List<Long> userIds = findUsersByLoginOrNameInCourse(loginOrName, courseId, pageable).stream().map(DomainObject::getId).toList();

        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        return findDistinctUsersWithGroupsByIdIn(userIds);
    }

    /**
     * Searches for users by login or name within a course and returns a paginated list of distinct users along with their groups.
     * This method avoids in-memory paging by retrieving the user IDs directly from the database.
     *
     * @param pageable    the pagination information
     * @param loginOrName the login or name of the users to search for
     * @param courseId    the ID of the course to search within
     * @return a {@code Page} containing a list of distinct users with their groups, or an empty page if no users are found
     */
    default Page<User> searchAllWithGroupsByLoginOrNameInCourseAndReturnPage(Pageable pageable, String loginOrName, long courseId) {
        List<Long> userIds = findUsersByLoginOrNameInCourse(loginOrName, courseId, pageable).stream().map(DomainObject::getId).toList();

        if (userIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<User> users = findDistinctUsersWithGroupsByIdIn(userIds);
        long total = countUsersByLoginOrNameInCourse(loginOrName, courseId);

        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT user.id
            FROM User user
            WHERE user.isDeleted = FALSE
            """)
    List<Long> findUserIdsByIsDeletedIsFalse(Pageable pageable);

    @Query("""
            SELECT COUNT(user)
            FROM User user
            WHERE user.isDeleted = FALSE
            """)
    long countUsersByIsDeletedIsFalse();

    /**
     * Retrieves a paginated list of {@link User} entities that are not marked as deleted,
     * with their associated groups.
     *
     * @param pageable the pagination information.
     * @return a paginated list of {@link User} entities that are not marked as deleted. If no entities are found, returns an empty page.
     */
    default Page<User> findAllWithGroupsByIsDeletedIsFalse(Pageable pageable) {
        List<Long> ids = findUserIdsByIsDeletedIsFalse(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersWithGroupsByIdIn(ids);
        long total = countUsersByIsDeletedIsFalse();
        return new PageImpl<>(users, pageable, total);
    }

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalse();

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.lastNotificationRead = :lastNotificationRead
            WHERE user.id = :userId
            """)
    void updateUserNotificationReadDate(@Param("userId") long userId, @Param("lastNotificationRead") ZonedDateTime lastNotificationRead);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.imageUrl = :imageUrl
            WHERE user.id = :userId
            """)
    void updateUserImageUrl(@Param("userId") long userId, @Param("imageUrl") String imageUrl);

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
    @Query("""
            UPDATE User user
            SET user.hideNotificationsUntil = :hideNotificationUntil
            WHERE user.id = :userId
            """)
    void updateUserNotificationVisibility(@Param("userId") long userId, @Param("hideNotificationUntil") ZonedDateTime hideNotificationUntil);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.langKey = :languageKey
            WHERE user.id = :userId
            """)
    void updateUserLanguageKey(@Param("userId") long userId, @Param("languageKey") String languageKey);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.sshPublicKeyHash = :sshPublicKeyHash,
                user.sshPublicKey = :sshPublicKey
            WHERE user.id = :userId
            """)
    void updateUserSshPublicKeyHash(@Param("userId") long userId, @Param("sshPublicKeyHash") String sshPublicKeyHash, @Param("sshPublicKey") String sshPublicKey);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.vcsAccessToken = :vcsAccessToken,
                user.vcsAccessTokenExpiryDate = :vcsAccessTokenExpiryDate
            WHERE user.id = :userId
            """)
    void updateUserVcsAccessToken(@Param("userId") long userId, @Param("vcsAccessToken") String vcsAccessToken,
            @Param("vcsAccessTokenExpiryDate") ZonedDateTime vcsAccessTokenExpiryDate);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.irisAccepted = :acceptDatetime
            WHERE user.id = :userId
            """)
    void updateIrisAcceptedToDate(@Param("userId") long userId, @Param("acceptDatetime") ZonedDateTime acceptDatetime);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups userGroup
            WHERE user.isDeleted = FALSE
                AND :groupName = userGroup
                AND user NOT IN :ignoredUsers
            """)
    Set<User> findAllInGroupContainingAndNotIn(@Param("groupName") String groupName, @Param("ignoredUsers") Set<User> ignoredUsers);

    @Query("""
            SELECT DISTINCT team.students AS student
            FROM Team team
                JOIN team.students st
            WHERE st.isDeleted = FALSE
                AND team.exercise.course.id = :courseId
                AND team.shortName = :teamShortName
            """)
    Set<User> findAllInTeam(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    /**
     * Get all logins of users that are not enrolled in any course,
     * without administrators which are normally not enrolled in any course.
     *
     * @return all logins of not enrolled users as a sorted list (not admins)
     */
    @Query("""
            SELECT user.login
            FROM User user
            WHERE user.groups IS EMPTY AND NOT user.isDeleted
                AND NOT :#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
            ORDER BY user.login
            """)
    List<String> findAllNotEnrolledUsers();

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

        // Users without registration numbers or with registration numbers
        var noRegistrationNumber = userSearch.getRegistrationNumbers().contains(FILTER_WITHOUT_REG_NO);
        var withRegistrationNumber = userSearch.getRegistrationNumbers().contains(FILTER_WITH_REG_NO);

        Specification<User> specification = Specification.where(distinct()).and(notSoftDeleted()).and(getSearchTermSpecification(searchTerm))
                .and(getInternalOrExternalSpecification(internal, external)).and(getActivatedOrDeactivatedSpecification(activated, deactivated))
                .and(getAuthoritySpecification(modifiedAuthorities)).and(getWithOrWithoutRegistrationNumberSpecification(noRegistrationNumber, withRegistrationNumber));

        if (userSearch.isFindWithoutUserGroups()) {
            specification = specification.and(getAllUsersWithoutUserGroups());
        }

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
        return getValueElseThrow(findOneByLogin(currentUserLogin));
    }

    /**
     * Finds user id by login
     *
     * @param login the login of the user to search
     * @return optional of the user id if it exists, empty otherwise
     */
    @Query("""
            SELECT u.id
            FROM User u
            WHERE u.login = :login
            """)
    Optional<Long> findIdByLogin(@Param("login") String login);

    /**
     * Get the user id of the currently logged-in user
     *
     * @return the user id of the currently logged-in user
     */
    default long getUserIdElseThrow() {
        String currentUserLogin = getCurrentUserLogin();
        return getArbitraryValueElseThrow(findIdByLogin(currentUserLogin), currentUserLogin);
    }

    /**
     * Retrieve a user by its login, or else throw exception
     *
     * @param login the login of the user to search
     * @return the user entity if it exists
     */
    @NotNull
    default User getUserByLoginElseThrow(String login) {
        return getValueElseThrow(findOneByLogin(login));
    }

    /**
     * Get user with user groups and authorities of currently logged-in user
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAndAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        return getValueElseThrow(findOneWithGroupsAndAuthoritiesByLogin(currentUserLogin));
    }

    /**
     * Get user with user groups, authorities and organizations of currently logged-in user
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAndAuthoritiesAndOrganizations() {
        String currentUserLogin = getCurrentUserLogin();
        return getValueElseThrow(findOneWithGroupsAndAuthoritiesAndOrganizationsByLogin(currentUserLogin));
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
        return getValueElseThrow(findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(currentUserLogin));
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
        return getValueElseThrow(findOneWithGroupsAndAuthoritiesByLogin(username));
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
        return getValueElseThrow(findOneWithGroupsAndAuthoritiesById(userId), userId);
    }

    /**
     * Find user with eagerly loaded groups, authorities and organizations by its id
     *
     * @param userId the id of the user to find
     * @return the user with groups, authorities and organizations if it exists, else throw exception
     */
    @NotNull
    default User findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(long userId) {
        return getValueElseThrow(findOneWithGroupsAndAuthoritiesAndOrganizationsById(userId), userId);
    }

    /**
     * Find user with eagerly loaded learning paths by its id
     *
     * @param userId the id of the user to find
     * @return the user with learning paths if it exists, else throw exception
     */
    @NotNull
    default User findWithLearningPathsByIdElseThrow(long userId) {
        return getValueElseThrow(findWithLearningPathsById(userId), userId);
    }

    /**
     * Get students by given course
     *
     * @param course object
     * @return students for given course
     */
    default Set<User> getStudents(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getStudentGroupName());
    }

    /**
     * Get tutors by given course
     *
     * @param course object
     * @return tutors for given course
     */
    default Set<User> getTutors(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getTeachingAssistantGroupName());
    }

    /**
     * Get editors by given course
     *
     * @param course object
     * @return editors for given course
     */
    default Set<User> getEditors(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getEditorGroupName());
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return instructors for the given course
     */
    default Set<User> getInstructors(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getInstructorGroupName());
    }

    /**
     * Get all users for a given course
     *
     * @param course The course for which to fetch all users
     * @return all users in the course
     */
    default Set<User> getUsersInCourse(Course course) {
        Set<String> groupNames = Set.of(course.getStudentGroupName(), course.getTeachingAssistantGroupName(), course.getEditorGroupName(), course.getInstructorGroupName());
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(groupNames);
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
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(groupName);
    }

    default Long countUserInGroup(String groupName) {
        return countByIsDeletedIsFalseAndGroupsContains(groupName);
    }

    /**
     * Update user notification read date for current user
     *
     * @param userId the user for which the notification read date should be updated
     */
    default void updateUserNotificationReadDate(long userId) {
        updateUserNotificationReadDate(userId, ZonedDateTime.now());
    }

    @Query(value = """
            SELECT *
            FROM jhi_user u
            WHERE is_deleted = FALSE
                AND REGEXP_LIKE(u.email, :emailPattern)
            """, nativeQuery = true)
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

    Optional<User> findBySshPublicKeyHash(String keyString);

    /**
     * Finds all users which a non-null VCS access token that expires before some given date.
     *
     * @param expirationDate the maximal expiration date of the retrieved users
     * @return all users with expiring VCS access tokens before the given date
     */
    @Query("""
            SELECT user
            FROM User user
            WHERE user.vcsAccessToken IS NOT NULL
                AND user.vcsAccessTokenExpiryDate IS NOT NULL
                AND user.vcsAccessTokenExpiryDate <= :date
            """)
    Set<User> getUsersWithAccessTokenExpirationDateBefore(@Param("date") ZonedDateTime expirationDate);

    /**
     * Finds all users with VCS access tokens set to null.
     *
     * @return all users without VCS access tokens
     */
    @Query("""
            SELECT user
            FROM User user
            WHERE user.vcsAccessToken IS NULL
            """)
    Set<User> getUsersWithAccessTokenNull();

    @Query("""
            SELECT user.login
            FROM User user
            WHERE :#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                AND user.activated = TRUE
                AND user.isDeleted = FALSE
            """)
    Set<String> findAllActiveAdminLogins();

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND :#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
            """)
    boolean isAdmin(@Param("login") String login);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.studentGroupName MEMBER OF user.groups)
                    OR (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Course course
            ON user.login = :login
                AND course.id = :courseId
            WHERE (course.instructorGroupName MEMBER OF user.groups)
                OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            LEFT JOIN exercise.course course
            LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course.studentGroupName MEMBER OF user.groups)
                    OR (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (examCourse.studentGroupName MEMBER OF user.groups)
                    OR (examCourse.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (examCourse.editorGroupName MEMBER OF user.groups)
                    OR (examCourse.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            LEFT JOIN exercise.course course
            LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (examCourse.teachingAssistantGroupName MEMBER OF user.groups)
                    OR (examCourse.editorGroupName MEMBER OF user.groups)
                    OR (examCourse.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            LEFT JOIN exercise.course course
            LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course.editorGroupName MEMBER OF user.groups)
                    OR (course.instructorGroupName MEMBER OF user.groups)
                    OR (examCourse.editorGroupName MEMBER OF user.groups)
                    OR (examCourse.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            INNER JOIN Exercise exercise
            ON user.login = :login
                AND exercise.id = :exerciseId
            LEFT JOIN exercise.course course
            LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course.instructorGroupName MEMBER OF user.groups)
                    OR (examCourse.instructorGroupName MEMBER OF user.groups)
                    OR (:#{T(de.tum.cit.aet.artemis.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);
}
