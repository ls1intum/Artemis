package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.account.repository.UserSpecs.distinct;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.getActivatedOrDeactivatedSpecification;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.getAllUsersWithoutCourseEnrollment;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.getAuthoritySpecification;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.getInternalOrExternalSpecification;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.getSearchTermSpecification;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.getWithOrWithoutRegistrationNumberSpecification;
import static de.tum.cit.aet.artemis.account.repository.UserSpecs.notSoftDeleted;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.CourseRoleCountDTO;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.UserRoleDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.UserPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.dto.StudentDTO;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.deleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserRepository extends ArtemisJpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String FILTER_INTERNAL = "INTERNAL";

    String FILTER_EXTERNAL = "EXTERNAL";

    String FILTER_ACTIVATED = "ACTIVATED";

    String FILTER_DEACTIVATED = "DEACTIVATED";

    String FILTER_WITH_REG_NO = "WITH_REG_NO";

    String FILTER_WITHOUT_REG_NO = "WITHOUT_REG_NO";

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    List<User> findByVcsAccessTokenExpiryDateBetween(ZonedDateTime from, ZonedDateTime to);

    Optional<User> findOneByLogin(String login);

    Optional<User> findOneByActivationKey(String activationKey);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Set<User> findAllWithAuthoritiesByDeletedIsFalseAndLoginIn(Set<String> logins);

    @EntityGraph(type = LOAD, attributePaths = { "courseRoles", "authorities" })
    Optional<User> findOneWithCourseRolesAndAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "courseRoles", "authorities" })
    Optional<User> findOneWithCourseRolesAndAuthoritiesById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByRegistrationNumber(String registrationNumber);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByEmail(String email);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByLoginAndInternal(String login, boolean internal);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByEmailAndInternal(String email, boolean internal);

    @EntityGraph(type = LOAD, attributePaths = { "authorities", "organizations" })
    Optional<User> findOneWithAuthoritiesAndOrganizationsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "courseRoles", "authorities", "organizations" })
    Optional<User> findOneWithCourseRolesAndAuthoritiesAndOrganizationsById(Long id);

    @Query("""
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.authorities
            LEFT JOIN FETCH u.learnerProfile lp
            LEFT JOIN FETCH lp.courseLearnerProfiles clp
            WHERE u.login = :login
                AND clp.course.id = :courseId
            """)
    Optional<User> findOneWithAuthoritiesAndLearnerProfileByLogin(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT u FROM User u
                JOIN u.courseRoles ucr
            WHERE u.login = :login
                AND u.deleted = FALSE
                AND ucr.course.id = :courseId
                AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
            """)
    Optional<User> findStudentByLoginAndCourseId(@Param("login") String login, @Param("courseId") long courseId);

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
     * @return a set of {@link UserRoleDTO} objects containing the user ID, user login, and role for each user
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.UserRoleDTO(user.id, user.login,
                   CASE
                       WHEN :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities THEN 'INSTRUCTOR'
                       WHEN :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities THEN 'INSTRUCTOR'
                       WHEN EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                           AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR) THEN 'INSTRUCTOR'
                       WHEN EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                           AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                               de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT)) THEN 'TUTOR'
                       WHEN EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                           AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT) THEN 'USER'
                   END)
            FROM User user
            WHERE user.id IN :userIds
            """)
    Set<UserRoleDTO> findUserRolesInCourse(@Param("userIds") Collection<Long> userIds, @Param("courseId") long courseId);

    @Query("""
            SELECT user
            FROM User user
            JOIN user.organizations organization
            WHERE organization.id = :organizationId
            """)
    Set<User> findAllByOrganizationId(@Param("organizationId") Long organizationId);

    @Query("""
            SELECT DISTINCT user
            FROM User user
            WHERE user.deleted = FALSE
                AND (
                    LOWER(user.email) = LOWER(:searchInput)
                    OR LOWER(user.login) = LOWER(:searchInput)
                )
            """)
    List<User> findAllByEmailOrUsernameIgnoreCase(@Param("searchInput") String searchInput);

    /**
     * Fetches all non-deleted users enrolled in a course with the given role, eagerly loading their
     * authorities and learner profile (including course learner profiles).
     *
     * @param courseId the ID of the course
     * @param role     the course role to filter by
     * @return set of matching users (authorities and learner profile initialized)
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.authorities
                LEFT JOIN FETCH user.learnerProfile
            WHERE user.deleted = FALSE
                AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user
                    AND ucr.course.id = :courseId AND ucr.role = :role)
            """)
    Set<User> findAllWithAuthoritiesAndLearnerProfileByCourseIdAndRole(@Param("courseId") long courseId, @Param("role") CourseRole role);

    @Query("""
            SELECT DISTINCT new de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary (
                user.id,
                user.login,
                user.firstName,
                user.lastName,
                user.langKey,
                user.email,
                CASE WHEN cp.isMuted = TRUE THEN TRUE ELSE FALSE END,
                CASE WHEN cp.isHidden = TRUE THEN TRUE ELSE FALSE END,
                CASE WHEN EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)) THEN TRUE ELSE FALSE END
            )
            FROM User user
                JOIN user.courseRoles memberRole ON memberRole.course.id = :courseId
                LEFT JOIN ConversationParticipant cp ON cp.user = user AND cp.conversation.id = :conversationId
            WHERE user.deleted = FALSE
            """)
    Set<ConversationNotificationRecipientSummary> findAllNotificationRecipientsInCourseForConversation(@Param("conversationId") long conversationId,
            @Param("courseId") long courseId);

    /**
     * Searches for users in a course with a specific role by their login or full name.
     *
     * @param courseId    ID of the course to search within
     * @param role        the {@link CourseRole} to filter by
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id
                AND ucr.course.id = :courseId
                AND ucr.role = :role
            WHERE user.deleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    List<User> searchByLoginOrNameInCourseWithRole(@Param("courseId") long courseId, @Param("role") CourseRole role, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users by their full name in a course (any role).
     *
     * @param courseId   ID of the course in which to search
     * @param nameOfUser name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT user
            FROM User user
            WHERE user.deleted = FALSE
                AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId)
                AND CONCAT(user.firstName, ' ', user.lastName) LIKE %:nameOfUser%
            ORDER BY CONCAT(user.firstName, ' ', user.lastName)
            """)
    List<User> searchByNameInCourse(@Param("courseId") long courseId, @Param("nameOfUser") String nameOfUser);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.deleted = FALSE
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
            WHERE user.deleted = FALSE
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
    default Page<User> searchAllWithCourseRolesByLoginOrNameInConversation(Pageable pageable, String loginOrName, long conversationId) {
        List<Long> ids = findUsersByLoginOrNameInConversation(loginOrName, conversationId, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids);
        long total = countUsersByLoginOrNameInConversation(loginOrName, conversationId);
        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT DISTINCT user.id
            FROM User user
                JOIN ConversationParticipant cp ON cp.user.id = user.id AND cp.conversation.id = :conversationId
                JOIN UserCourseRole ucr ON ucr.user.id = user.id AND ucr.course.id = :courseId AND ucr.role IN :roles
            WHERE user.deleted = FALSE
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            ORDER BY user.id ASC
            """)
    List<Long> findUserIdsByLoginOrNameInConversationWithCourseRoles(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId,
            @Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN ConversationParticipant cp ON cp.user.id = user.id AND cp.conversation.id = :conversationId
                JOIN UserCourseRole ucr ON ucr.user.id = user.id AND ucr.course.id = :courseId AND ucr.role IN :roles
            WHERE user.deleted = FALSE
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    long countUsersByLoginOrNameInConversationWithCourseRoles(@Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId,
            @Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles);

    /**
     * Searches for {@link User} entities by login or name within a specific conversation, filtered by their course roles.
     * The results are paginated.
     *
     * @param pageable       the pagination information.
     * @param loginOrName    the login or name to search for.
     * @param conversationId the ID of the conversation to limit the search within.
     * @param courseId       the ID of the course to filter by.
     * @param roles          the set of course roles to filter by.
     * @return a paginated list of {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchAllWithCourseRolesByLoginOrNameInConversation(Pageable pageable, String loginOrName, long conversationId, long courseId, Set<CourseRole> roles) {
        // Use an unsorted pageable for the ID lookup: SELECT DISTINCT user.id cannot ORDER BY firstName/lastName (not in SELECT)
        // The final result ordering is applied by findUsersByIdsWithCourseRolesOrdered.
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        List<Long> ids = findUserIdsByLoginOrNameInConversationWithCourseRoles(loginOrName, conversationId, courseId, roles, unsortedPageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids);
        long total = countUsersByLoginOrNameInConversationWithCourseRoles(loginOrName, conversationId, courseId, roles);
        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.deleted = FALSE
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
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.deleted = FALSE
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
    default Page<User> searchChannelModeratorsWithCourseRolesByLoginOrNameInConversation(Pageable pageable, String loginOrName, long conversationId) {
        List<Long> ids = findModeratorsByLoginOrNameInConversation(loginOrName, conversationId, pageable).stream().map(DomainObject::getId).toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids); // these users are moderators
        long total = countModeratorsByLoginOrNameInConversation(loginOrName, conversationId);
        return new PageImpl<>(users, pageable, total);
    }

    /**
     * Finds all non-deleted users enrolled in a course with the given role whose login is in the given set.
     *
     * @param courseId the ID of the course
     * @param role     the course role to filter by
     * @param logins   the set of logins to search for
     * @return list of matching users
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.courseRoles ucr
            WHERE ucr.course.id = :courseId
                AND ucr.role = :role
                AND user.login IN :logins
                AND user.deleted = FALSE
            """)
    List<User> findAllByCourseIdAndRoleAndLoginIn(@Param("courseId") long courseId, @Param("role") CourseRole role, @Param("logins") Set<String> logins);

    /**
     * Finds all non-deleted users enrolled in a course with the given role whose registration number is in the given set.
     *
     * @param courseId            the ID of the course
     * @param role                the course role to filter by
     * @param registrationNumbers the set of registration numbers to search for
     * @return list of matching users
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.courseRoles ucr
            WHERE ucr.course.id = :courseId
                AND ucr.role = :role
                AND user.registrationNumber IN :registrationNumbers
                AND user.deleted = FALSE
            """)
    List<User> findAllByCourseIdAndRoleAndRegistrationNumberIn(@Param("courseId") long courseId, @Param("role") CourseRole role,
            @Param("registrationNumbers") Set<String> registrationNumbers);

    /**
     * Fetches all non-deleted users enrolled in a course with any of the given roles.
     *
     * @param courseId the ID of the course
     * @param roles    the set of {@link CourseRole} values to filter by
     * @return set of matching users
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.courseRoles ucr
            WHERE ucr.course.id = :courseId
                AND ucr.role IN :roles
                AND user.deleted = FALSE
            """)
    Set<User> findAllByCourseIdAndCourseRolesIn(@Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles);

    /**
     * Fetches all non-deleted users enrolled in a course with any of the given roles, with their {@code authorities} collection eagerly initialized.
     * Use this variant when the callers need to access {@code user.getAuthorities()} without an open Hibernate session (e.g. to call {@link AuthorizationCheckService#isAdmin}).
     *
     * @param courseId the ID of the course
     * @param roles    the set of {@link CourseRole} values to filter by
     * @return set of matching users (authorities initialized)
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.authorities
                JOIN user.courseRoles ucr
            WHERE ucr.course.id = :courseId
                AND ucr.role IN :roles
                AND user.deleted = FALSE
            """)
    Set<User> findAllByCourseIdAndCourseRolesInWithAuthorities(@Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles);

    @Query("""
            SELECT COUNT(DISTINCT ucr.user)
            FROM UserCourseRole ucr
            WHERE ucr.course.id = :courseId
                AND ucr.role = :role
                AND ucr.user.deleted = FALSE
            """)
    long countByCourseIdAndRole(@Param("courseId") long courseId, @Param("role") CourseRole role);

    List<User> findAllByIdIn(Collection<Long> ids);

    @Query("""
            SELECT DISTINCT NEW de.tum.cit.aet.artemis.exercise.dto.StudentDTO(u.id, u.login, u.firstName, u.lastName, u.registrationNumber, u.email)
            FROM User u
            WHERE u.id IN :ids
            """)
    List<StudentDTO> findAllStudentsByIdIn(@Param("ids") Collection<Long> ids);

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
            WHERE user.deleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users by login (prefix), full name (contains), email (contains), or registration number (contains).
     * Used for the generic user-registration modal to find users that can be added to an entity (e.g. exam).
     * Escapes LIKE wildcard characters ({@code %}, {@code _}, {@code \}) in {@code searchTerm} before querying.
     *
     * @param page       Pageable controlling page index and size
     * @param searchTerm the search string entered by the user
     * @return a page of matching users
     */
    default Page<User> searchAllByLoginOrNameOrEmailOrRegistrationNumber(Pageable page, String searchTerm) {
        if (!StringUtils.hasText(searchTerm)) {
            return Page.empty(page);
        }
        String escaped = searchTerm.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return findAllByLoginOrNameOrEmailOrRegistrationNumber(page, escaped);
    }

    @Query("""
            SELECT user
            FROM User user
            WHERE user.deleted = FALSE
                AND (
                    LOWER(user.login) LIKE :#{#searchTerm}% ESCAPE '\\'
                    OR LOWER(CONCAT(user.firstName, ' ', user.lastName)) LIKE %:#{#searchTerm}% ESCAPE '\\'
                    OR LOWER(user.email) LIKE %:#{#searchTerm}% ESCAPE '\\'
                    OR LOWER(user.registrationNumber) LIKE %:#{#searchTerm}% ESCAPE '\\'
                )
            """)
    Page<User> findAllByLoginOrNameOrEmailOrRegistrationNumber(Pageable page, @Param("searchTerm") String searchTerm);

    @Query("""
            SELECT DISTINCT user.id
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id AND ucr.course.id = :courseId
            WHERE user.deleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    List<Long> findUserIdsByLoginOrNameInCourse(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id AND ucr.course.id = :courseId
            WHERE user.deleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    long countUserIdsByLoginOrNameInCourse(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId);

    /**
     * Searches for {@link User} entities by login or name within a specific course, eager-loading their course roles.
     * The results are paginated.
     *
     * @param pageable    the pagination information.
     * @param loginOrName the login or name to search for.
     * @param courseId    the ID of the course to limit the search within.
     * @return a paginated list of {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchAllWithCourseRolesByLoginOrNameInCourseAndReturnPage(Pageable pageable, String loginOrName, long courseId) {
        List<Long> userIds = findUserIdsByLoginOrNameInCourse(loginOrName, courseId, pageable);
        if (userIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(userIds);
        long total = countUserIdsByLoginOrNameInCourse(loginOrName, courseId);
        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT DISTINCT user.id
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id
                AND ucr.course.id = :courseId
                AND ucr.role IN :roles
            WHERE user.deleted = FALSE
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                )
            """)
    List<Long> findUserIdsByLoginOrNameInCourseWithRoles(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles,
            Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id
                AND ucr.course.id = :courseId
                AND ucr.role IN :roles
            WHERE user.deleted = FALSE
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                )
            """)
    long countUsersByLoginOrNameInCourseWithRoles(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles);

    /**
     * Searches for users by login or name within a course filtered by specific roles.
     *
     * @param pageable    the pagination information
     * @param loginOrName the login or name to search for
     * @param courseId    the ID of the course to search within
     * @param roles       the set of {@link CourseRole} values to filter by
     * @return a paginated list of matching {@link User} entities, or an empty page if none found
     */
    default Page<User> searchAllWithCourseRolesByLoginOrNameInCourse(Pageable pageable, String loginOrName, long courseId, Set<CourseRole> roles) {
        List<Long> ids = findUserIdsByLoginOrNameInCourseWithRoles(loginOrName, courseId, roles, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids);
        long total = countUsersByLoginOrNameInCourseWithRoles(loginOrName, courseId, roles);
        return new PageImpl<>(users, pageable, total);
    }

    /**
     * Searches for users by login or name within a course filtered by specific roles and converts results to {@link UserDTO}.
     *
     * @param pageable    the pagination information
     * @param loginOrName the login or name to search for
     * @param courseId    the ID of the course to search within
     * @param roles       the set of {@link CourseRole} values to filter by
     * @return a paginated list of matching users as {@link UserDTO}, or an empty page if none found
     */
    default Page<UserDTO> searchUsersByLoginOrNameInCourseWithRolesAndConvertToDTO(Pageable pageable, String loginOrName, long courseId, Set<CourseRole> roles) {
        List<Long> ids = findUserIdsByLoginOrNameInCourseWithRoles(loginOrName, courseId, roles, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids);
        long total = countUsersByLoginOrNameInCourseWithRoles(loginOrName, courseId, roles);
        return new PageImpl<>(users, pageable, total).map(UserDTO::new);
    }

    // --- courseRoles-based search variants ---

    @Query("""
            SELECT DISTINCT user.id
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id
                AND ucr.course.id = :courseId
                AND ucr.role IN :roles
            WHERE user.deleted = FALSE
                AND user.id <> :idOfUser
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                )
            """)
    List<Long> findUserIdsByLoginOrNameInCourseWithRolesNotUserId(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles,
            @Param("idOfUser") long idOfUser, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
            JOIN UserCourseRole ucr ON ucr.user.id = user.id
                AND ucr.course.id = :courseId
                AND ucr.role IN :roles
            WHERE user.deleted = FALSE
                AND user.id <> :idOfUser
                AND (
                    user.login LIKE %:loginOrName%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:loginOrName%
                )
            """)
    long countUsersByLoginOrNameInCourseWithRolesNotUserId(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, @Param("roles") Set<CourseRole> roles,
            @Param("idOfUser") long idOfUser);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.courseRoles
            WHERE user.id IN :ids
            ORDER BY user.firstName, user.lastName
            """)
    List<User> findUsersByIdsWithCourseRolesOrdered(@Param("ids") List<Long> ids);

    /**
     * Searches for {@link User} entities by login or name within a specific course, filtered by course roles,
     * excluding a specific user. The results are paginated.
     *
     * @param pageable    the pagination information.
     * @param loginOrName the login or name to search for.
     * @param courseId    the ID of the course to limit the search within.
     * @param roles       the set of course roles to filter by.
     * @param idOfUser    the ID of the user to exclude from the results.
     * @return a paginated list of {@link User} entities matching the search criteria. If no entities are found, returns an empty page.
     */
    default Page<User> searchAllWithCourseRolesByLoginOrNameInCourseNotUserId(Pageable pageable, String loginOrName, long courseId, Set<CourseRole> roles, long idOfUser) {
        List<Long> ids = findUserIdsByLoginOrNameInCourseWithRolesNotUserId(loginOrName, courseId, roles, idOfUser, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids);
        return new PageImpl<>(users, pageable, countUsersByLoginOrNameInCourseWithRolesNotUserId(loginOrName, courseId, roles, idOfUser));
    }

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.imageUrl = :imageUrl
            WHERE user.id = :userId
            """)
    void updateUserImageUrl(@Param("userId") long userId, @Param("imageUrl") String imageUrl);

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
            SET user.vcsAccessToken = :vcsAccessToken,
                user.vcsAccessTokenExpiryDate = :vcsAccessTokenExpiryDate
            WHERE user.id = :userId
            """)
    void updateUserVcsAccessToken(@Param("userId") long userId, @Param("vcsAccessToken") String vcsAccessToken,
            @Param("vcsAccessTokenExpiryDate") ZonedDateTime vcsAccessTokenExpiryDate);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User user
            SET user.aiSelectionDecision = :decision,
                user.aiSelectionDecisionDate = :timestamp
            WHERE user.id = :userId
            """)
    void updateSelectedLLMUsage(@Param("userId") long userId, @Param("decision") AiSelectionDecision decision, @Param("timestamp") ZonedDateTime timestamp);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.memirisEnabled = :memirisEnabled
            WHERE user.id = :userId
            """)
    void updateMemirisEnabled(@Param("userId") long userId, @Param("memirisEnabled") boolean memirisEnabled);

    @Query("""
            SELECT DISTINCT team.students AS student
            FROM Team team
                JOIN team.students st
            WHERE st.deleted = FALSE
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
            WHERE NOT user.deleted
                AND NOT EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user)
                AND NOT :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                AND NOT :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities
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

        Specification<User> specification = distinct().and(notSoftDeleted()).and(getSearchTermSpecification(searchTerm)).and(getInternalOrExternalSpecification(internal, external))
                .and(getActivatedOrDeactivatedSpecification(activated, deactivated)).and(getAuthoritySpecification(modifiedAuthorities))
                .and(getWithOrWithoutRegistrationNumberSpecification(noRegistrationNumber, withRegistrationNumber));

        if (userSearch.isFindWithoutCourseEnrollment()) {
            specification = specification.and(getAllUsersWithoutCourseEnrollment());
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
    @NonNull
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
    @NonNull
    default User getUserByLoginElseThrow(String login) {
        return getValueElseThrow(findOneByLogin(login));
    }

    /**
     * Retrieve a user by its email (ignoring case), or else throw exception
     *
     * @param email the email of the user to search
     * @return the user entity if it exists
     */
    @NonNull
    default User getUserByEmailElseThrow(String email) {
        return getValueElseThrow(findOneByEmailIgnoreCase(email));
    }

    /**
     * Get the login of the currently logged-in user.
     * If no user is logged in, an exception is thrown.
     *
     * @return the login of the currently logged-in user
     * @throws EntityNotFoundException if no user is logged in
     */
    default String getCurrentUserLogin() {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            return currentUserLogin.get();
        }
        throw new EntityNotFoundException("ERROR: No current user login found!");
    }

    @NonNull
    default User getUserWithAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        return getValueElseThrow(findOneWithAuthoritiesByLogin(currentUserLogin));
    }

    @NonNull
    default User getUserWithAuthorities(@NonNull String login) {
        return getValueElseThrow(findOneWithAuthoritiesByLogin(login));
    }

    /**
     * Get user with authorities and organizations of currently logged-in user (no courseRoles loaded).
     * Use this when the caller needs org-membership checks but not courseRoles (e.g. enrollment eligibility).
     *
     * @return currently logged-in user with authorities and organizations
     */
    @NonNull
    default User getUserWithAuthoritiesAndOrganizations() {
        String currentUserLogin = getCurrentUserLogin();
        return getValueElseThrow(findOneWithAuthoritiesAndOrganizationsByLogin(currentUserLogin));
    }

    /**
     * Get user with authorities and learner profile of currently logged-in user (no courseRoles loaded).
     *
     * @param courseId the id of the course for which to load the course learner profile
     * @return currently logged-in user with authorities and learner profile
     */
    @NonNull
    default User getUserWithAuthoritiesAndLearnerProfile(long courseId) {
        String currentUserLogin = getCurrentUserLogin();
        return getValueElseThrow(findOneWithAuthoritiesAndLearnerProfileByLogin(currentUserLogin, courseId));
    }

    default Optional<User> findUserWithAuthoritiesByRegistrationNumber(String registrationNumber) {
        if (!StringUtils.hasText(registrationNumber)) {
            return Optional.empty();
        }
        return findOneWithAuthoritiesByRegistrationNumber(registrationNumber);
    }

    /**
     * Finds a single user with authorities using the login name.
     * Returns {@link Optional#empty()} if the login is null or blank.
     *
     * @param login user login string
     * @return the user with authorities, or empty if login is blank or user not found
     */
    default Optional<User> findUserWithAuthoritiesByLogin(String login) {
        if (!StringUtils.hasText(login)) {
            return Optional.empty();
        }
        return findOneWithAuthoritiesByLogin(login);
    }

    /**
     * Finds a single user with authorities using the email address.
     * Returns {@link Optional#empty()} if the email is null or blank.
     *
     * @param email user email string
     * @return the user with authorities, or empty if email is blank or user not found
     */
    default Optional<User> findUserWithAuthoritiesByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return findOneWithAuthoritiesByEmail(email);
    }

    @NonNull
    default User findByIdWithCourseRolesAndAuthoritiesElseThrow(long userId) {
        return getValueElseThrow(findOneWithCourseRolesAndAuthoritiesById(userId), userId);
    }

    @NonNull
    default User findByIdWithAuthoritiesElseThrow(long userId) {
        return getValueElseThrow(findOneWithAuthoritiesById(userId), userId);
    }

    /**
     * Find user with eagerly loaded course roles, authorities and organizations by its id
     *
     * @param userId the id of the user to find
     * @return the user with course roles, authorities and organizations if it exists, else throw exception
     */
    @NonNull
    default User findByIdWithCourseRolesAndAuthoritiesAndOrganizationsElseThrow(long userId) {
        return getValueElseThrow(findOneWithCourseRolesAndAuthoritiesAndOrganizationsById(userId), userId);
    }

    /**
     * Get students by given course
     *
     * @param course object
     * @return students for given course
     */
    default Set<User> getStudents(Course course) {
        return findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.STUDENT));
    }

    /**
     * Get students by given course with their learner Profile
     *
     * @param course object
     * @return students for given course
     */
    default Set<User> getStudentsWithLearnerProfile(Course course) {
        return findAllWithAuthoritiesAndLearnerProfileByCourseIdAndRole(course.getId(), CourseRole.STUDENT);
    }

    /**
     * Get tutors by given course
     *
     * @param course object
     * @return tutors for given course
     */
    default Set<User> getTutors(Course course) {
        return findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.TEACHING_ASSISTANT));
    }

    /**
     * Get editors by given course
     *
     * @param course object
     * @return editors for given course
     */
    default Set<User> getEditors(Course course) {
        return findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.EDITOR));
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return instructors for the given course
     */
    default Set<User> getInstructors(Course course) {
        return findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.INSTRUCTOR));
    }

    /**
     * Get all users for a given course
     *
     * @param course The course for which to fetch all users
     * @return all users in the course
     */
    default Set<User> getUsersInCourse(Course course) {
        return findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.STUDENT, CourseRole.TEACHING_ASSISTANT, CourseRole.EDITOR, CourseRole.INSTRUCTOR));
    }

    /**
     * Batch-counts non-deleted users with the given role across multiple courses.
     *
     * @param courseIds the course ids to count for
     * @param role      the role to count
     * @return list of (courseId, role, count) triples — courses with zero members are omitted
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseRoleCountDTO(ucr.course.id, ucr.role, COUNT(DISTINCT ucr.user))
            FROM UserCourseRole ucr
            WHERE ucr.course.id IN :courseIds
                AND ucr.role = :role
                AND ucr.user.deleted = FALSE
            GROUP BY ucr.course.id, ucr.role
            """)
    List<CourseRoleCountDTO> countByCourseIdsAndRole(@Param("courseIds") Set<Long> courseIds, @Param("role") CourseRole role);

    /**
     * Counts non-deleted users for every role across multiple courses in a single query.
     *
     * @param courseIds the course ids to count for
     * @return list of (courseId, role, count) triples — courses/roles with zero members are omitted
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseRoleCountDTO(ucr.course.id, ucr.role, COUNT(DISTINCT ucr.user))
            FROM UserCourseRole ucr
            WHERE ucr.course.id IN :courseIds
                AND ucr.user.deleted = FALSE
            GROUP BY ucr.course.id, ucr.role
            """)
    List<CourseRoleCountDTO> countAllRolesByCourseIds(@Param("courseIds") Set<Long> courseIds);

    /**
     * Counts non-deleted users for all roles of a course and sets the counts on the course object.
     *
     * @param course the course to set user counts for
     */
    default void setUserCountsForCourse(Course course) {
        setUserCountsForCourses(List.of(course));
    }

    /**
     * Counts non-deleted users for all roles of multiple courses and sets the counts on each course object.
     * Uses a single query for all courses and all roles combined.
     *
     * @param courses the courses to set user counts for
     */
    default void setUserCountsForCourses(List<Course> courses) {
        Set<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toSet());
        Map<Long, Map<CourseRole, Long>> countMap = countAllRolesByCourseIds(courseIds).stream()
                .collect(Collectors.groupingBy(CourseRoleCountDTO::courseId, Collectors.toMap(CourseRoleCountDTO::role, CourseRoleCountDTO::count)));

        for (Course course : courses) {
            var roleCounts = countMap.getOrDefault(course.getId(), Map.of());
            course.setNumberOfStudents(roleCounts.getOrDefault(CourseRole.STUDENT, 0L));
            course.setNumberOfTeachingAssistants(roleCounts.getOrDefault(CourseRole.TEACHING_ASSISTANT, 0L));
            course.setNumberOfEditors(roleCounts.getOrDefault(CourseRole.EDITOR, 0L));
            course.setNumberOfInstructors(roleCounts.getOrDefault(CourseRole.INSTRUCTOR, 0L));
        }
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
        User user = findByIdWithCourseRolesAndAuthoritiesAndOrganizationsElseThrow(userId);
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
        User user = findByIdWithCourseRolesAndAuthoritiesAndOrganizationsElseThrow(userId);
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

    @Query("""
            SELECT user.login
            FROM User user
            WHERE (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
                AND user.activated = TRUE
                AND user.deleted = FALSE
            """)
    Set<String> findAllActiveAdminLogins();

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAdmin(@Param("login") String login);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities
            """)
    boolean isSuperAdmin(@Param("login") String login);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND (
                    EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId)
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities
                )
            """)
    boolean isAtLeastStudentInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND (
                    EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                        AND ucr.role IN (
                            de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                            de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                            de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR
                        ))
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities
                )
            """)
    boolean isAtLeastTeachingAssistantInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND (
                    EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                        AND ucr.role IN (
                            de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                            de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR
                        ))
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities
                )
            """)
    boolean isAtLeastEditorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
            WHERE user.login = :login
                AND (
                    EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course.id = :courseId
                        AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities
                    OR :#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities
                )
            """)
    boolean isAtLeastInstructorInCourse(@Param("login") String login, @Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Exercise exercise ON user.login = :login AND exercise.id = :exerciseId
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Exercise exercise ON user.login = :login AND exercise.id = :exerciseId
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Exercise exercise ON user.login = :login AND exercise.id = :exerciseId
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Exercise exercise ON user.login = :login AND exercise.id = :exerciseId
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInExercise(@Param("login") String login, @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Participation participation ON user.login = :login AND participation.id = :participationId
                LEFT JOIN participation.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInParticipation(@Param("login") String login, @Param("participationId") long participationId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Participation participation ON user.login = :login AND participation.id = :participationId
                LEFT JOIN participation.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInParticipation(@Param("login") String login, @Param("participationId") long participationId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Participation participation ON user.login = :login AND participation.id = :participationId
                LEFT JOIN participation.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInParticipation(@Param("login") String login, @Param("participationId") long participationId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Participation participation ON user.login = :login AND participation.id = :participationId
                LEFT JOIN participation.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup.exam.course examCourse
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                OR (examCourse IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = examCourse
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInParticipation(@Param("login") String login, @Param("participationId") long participationId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN LectureUnit lectureUnit ON user.login = :login AND lectureUnit.id = :lectureUnitId
                LEFT JOIN lectureUnit.lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInLectureUnit(@Param("login") String login, @Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN LectureUnit lectureUnit ON user.login = :login AND lectureUnit.id = :lectureUnitId
                LEFT JOIN lectureUnit.lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInLectureUnit(@Param("login") String login, @Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN LectureUnit lectureUnit ON user.login = :login AND lectureUnit.id = :lectureUnitId
                LEFT JOIN lectureUnit.lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInLectureUnit(@Param("login") String login, @Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN LectureUnit lectureUnit ON user.login = :login AND lectureUnit.id = :lectureUnitId
                LEFT JOIN lectureUnit.lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInLectureUnit(@Param("login") String login, @Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Lecture lecture ON user.login = :login AND lecture.id = :lectureId
                LEFT JOIN lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastStudentInLecture(@Param("login") String login, @Param("lectureId") long lectureId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Lecture lecture ON user.login = :login AND lecture.id = :lectureId
                LEFT JOIN lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastTeachingAssistantInLecture(@Param("login") String login, @Param("lectureId") long lectureId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Lecture lecture ON user.login = :login AND lecture.id = :lectureId
                LEFT JOIN lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                        de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastEditorInLecture(@Param("login") String login, @Param("lectureId") long lectureId);

    @Query("""
            SELECT COUNT(user) > 0
            FROM User user
                INNER JOIN Lecture lecture ON user.login = :login AND lecture.id = :lectureId
                LEFT JOIN lecture.course course
            WHERE (course IS NOT NULL AND EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user = user AND ucr.course = course
                    AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).ADMIN_AUTHORITY} MEMBER OF user.authorities)
                OR (:#{T(de.tum.cit.aet.artemis.account.domain.Authority).SUPER_ADMIN_AUTHORITY} MEMBER OF user.authorities)
            """)
    boolean isAtLeastInstructorInLecture(@Param("login") String login, @Param("lectureId") long lectureId);

    @Query("""
            SELECT jhiUser
            FROM CalendarSubscriptionTokenStore store
                JOIN store.user jhiUser
                LEFT JOIN FETCH jhiUser.authorities
            WHERE store.token = :token
            """)
    Optional<User> findOneWithAuthoritiesByCalendarSubscriptionToken(@Param("token") String token);

    /**
     * Get the IDs of users who have submitted at least one submission since the given date.
     * Excludes users with 'test' in their login (case-insensitive).
     * <p>
     * This is used as the first step in the optimized active students count:
     * 1. Get active user IDs (this query)
     * 2. Count users by group, filtering to only active user IDs
     *
     * @param activeSince the date after which a submission counts as active
     * @return a set of user IDs who have submitted since activeSince
     */
    @Query("""
            SELECT DISTINCT p.student.id
            FROM StudentParticipation p
                JOIN p.submissions s
                JOIN p.student u
            WHERE s.submissionDate >= :activeSince
                AND LOWER(u.login) NOT LIKE '%test%'
            """)
    Set<Long> findActiveUserIdsSince(@Param("activeSince") ZonedDateTime activeSince);

    /**
     * Count non-deleted students per course, filtering to only the specified user IDs.
     * Used as the second step in the optimized active students count,
     * after getting active user IDs via {@link #findActiveUserIdsSince}.
     *
     * @param courseIds the set of course ids to count students for
     * @param userIds   the set of user IDs to count (typically active users)
     * @return a list of CourseRoleCountDTO with course id and count of matching students
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseRoleCountDTO(ucr.course.id, ucr.role, COUNT(DISTINCT ucr.user))
            FROM UserCourseRole ucr
            WHERE ucr.course.id IN :courseIds
                AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                AND ucr.user.id IN :userIds
                AND ucr.user.deleted = FALSE
            GROUP BY ucr.course.id, ucr.role
            """)
    List<CourseRoleCountDTO> countStudentsByCourseIdsAndUserIds(@Param("courseIds") Set<Long> courseIds, @Param("userIds") Set<Long> userIds);
}
