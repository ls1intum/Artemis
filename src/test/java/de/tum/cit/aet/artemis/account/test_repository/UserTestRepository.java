package de.tum.cit.aet.artemis.account.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

@Lazy
@Repository
@Primary
public interface UserTestRepository extends UserRepository {

    @Query("""
            SELECT user.id
            FROM User user
            WHERE user.deleted = FALSE
            """)
    List<Long> findUserIdsByDeletedIsFalse(Pageable pageable);

    @Query("""
            SELECT COUNT(user)
            FROM User user
            WHERE user.deleted = FALSE
            """)
    long countUsersByDeletedIsFalse();

    /**
     * Retrieves a paginated list of {@link User} entities that are not marked as deleted,
     * with their associated course roles.
     *
     * @param pageable the pagination information.
     * @return a paginated list of {@link User} entities that are not marked as deleted. If no entities are found, returns an empty page.
     */
    default Page<User> findAllWithCourseRolesByDeletedIsFalse(Pageable pageable) {
        List<Long> ids = findUserIdsByDeletedIsFalse(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersByIdsWithCourseRolesOrdered(ids);
        long total = countUsersByDeletedIsFalse();
        return new PageImpl<>(users, pageable, total);
    }

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths", "learnerProfile", "learnerProfile.courseLearnerProfiles" })
    Optional<User> findOneWithLearningPathsAndLearnerProfileByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findWithLearningPathsById(long userId);

    /**
     * Find user with eagerly loaded learning paths by its id
     *
     * @param userId the id of the user to find
     * @return the user with learning paths if it exists, else throw exception
     */
    @NonNull
    default User findWithLearningPathsByIdElseThrow(long userId) {
        return getValueElseThrow(findWithLearningPathsById(userId), userId);
    }

    @Query("""
            SELECT user
            FROM User user
            WHERE user.login LIKE CONCAT(:userPrefix, '%')
            """)
    Set<User> findAllByUserPrefix(String userPrefix);

    @Query("""
            SELECT user
            FROM User user
                LEFT JOIN FETCH user.examUsers
            WHERE user.login = :login
            """)
    Optional<User> findOneWithExamUsersByLogin(@Param("login") String login);

    /**
     * Batch-loads users with their lazy collections eagerly for the given set of logins.
     * Used by {@link #saveAllOrUpdate} to warm the persistence context in a single query instead of
     * issuing one {@code findOneWithAuthoritiesByLogin} query per user.
     * <p>
     * Unlike the production {@code findAllWithAuthoritiesByDeletedIsFalseAndLoginIn}, this
     * variant does NOT filter by {@code deleted = FALSE} — test users may be soft-deleted but
     * still need to be reset.
     *
     * @param logins the set of logins to load
     * @return users with eagerly initialised {@code authorities} and {@code courseRoles}
     */
    @EntityGraph(type = LOAD, attributePaths = { "authorities", "courseRoles" })
    @Query("SELECT u FROM User u WHERE u.login IN :logins")
    Set<User> findAllWithAuthoritiesAndCourseRolesByLoginIn(@Param("logins") Set<String> logins);

    /**
     * Batch-saves new users and resets existing ones to the state of the passed-in object.
     * <p>
     * Test users are "zombies": fresh Java objects that carry an existing DB id (see
     * {@code UserUtilService.createOrReuseExistingUser}) and plain {@code HashSet}s for their collections. Saving one
     * delegates to JPA {@code merge()}, which needs the managed entity's lazy collections to be initialised — otherwise
     * replacing an uninitialised {@code PersistentSet} queues a {@code CollectionUpdateAction} whose {@code compareTo}
     * calls {@code hasDeletes()} on a {@code null} snapshot and throws an NPE.
     * <p>
     * Warming the persistence context with those collections initialised is therefore enough, and {@code merge()} then
     * copies every field by itself. Do NOT replace this with a hand-written field-by-field copy: such a list silently
     * goes stale whenever a field is added to {@link User}, which already caused test users to keep a stale
     * {@code isTestUser} flag and disappear from statistics queries.
     *
     * @param users the list of users to persist or reset
     * @return the list of saved managed users, in input order
     */
    @Transactional
    default List<User> saveAllOrUpdate(List<User> users) {
        Set<String> existingLogins = users.stream().filter(user -> user.getId() != null).map(User::getLogin).collect(Collectors.toSet());
        if (!existingLogins.isEmpty()) {
            findAllWithAuthoritiesAndCourseRolesByLoginIn(existingLogins);
        }
        return saveAll(users);
    }
}
