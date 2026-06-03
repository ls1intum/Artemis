package de.tum.cit.aet.artemis.account.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByDeletedIsFalse();

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
     * with their associated groups.
     *
     * @param pageable the pagination information.
     * @return a paginated list of {@link User} entities that are not marked as deleted. If no entities are found, returns an empty page.
     */
    default Page<User> findAllWithGroupsByDeletedIsFalse(Pageable pageable) {
        List<Long> ids = findUserIdsByDeletedIsFalse(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<User> users = findUsersWithGroupsByIdIn(ids);
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
     * Saves a new user via persist(), or updates an existing user in-place within one transaction.
     * <p>
     * Using plain {@code save(freshUser)} for existing users (those with an ID) triggers JPA
     * {@code merge()}, which loads the managed entity with a lazy, uninitialized
     * {@code PersistentSet} for the legacy {@code groups} {@code @ElementCollection} ({@code sn = null}).
     * Hibernate then creates a <em>new</em> {@code PersistentSet(loaded=true, sn=null)} from the plain
     * {@code HashSet} in the detached entity. This causes a {@code NullPointerException} in
     * {@code PersistentSet.hasDeletes()} during {@code sortCollectionActions} at flush time.
     * <p>
     * This method avoids that by loading the user <strong>with groups eagerly</strong> (via the
     * inherited {@code findOneWithGroupsByLogin} EntityGraph) within the same {@code @Transactional}
     * scope, so the {@code PersistentSet} is managed and its snapshot ({@code sn}) is initialized.
     * The {@code groups} collection is then cleared in-place to prevent stale legacy strings from
     * accumulating in {@code user_groups}.
     * <p>
     * <strong>Phase-9 note:</strong> once the {@code groups} field is dropped from {@code User} (and
     * the {@code user_groups} table is deleted), this method can be replaced with plain
     * {@code save()}/{@code saveAll()}.
     *
     * @param freshUser the user to persist (new) or update (existing); must have all fields set
     * @return the saved or updated user
     */
    @Transactional
    default User saveOrUpdate(User freshUser) {
        if (freshUser.getId() == null) {
            return save(freshUser);
        }
        // Load with groups eagerly so the PersistentSet sn is initialized (prevents NPE at flush).
        User existing = findOneWithGroupsByLogin(freshUser.getLogin()).orElseThrow(() -> new IllegalStateException("User not found for reuse: " + freshUser.getLogin()));
        existing.setPassword(freshUser.getPassword());
        existing.setFirstName(freshUser.getFirstName());
        existing.setLastName(freshUser.getLastName());
        existing.setEmail(freshUser.getEmail());
        existing.setActivated(freshUser.getActivated());
        existing.setLangKey(freshUser.getLangKey());
        existing.setSelectedLLMUsageTimestamp(freshUser.getSelectedLLMUsageTimestamp());
        existing.setSelectedLLMUsage(freshUser.getSelectedLLMUsage());
        // Clear legacy group strings in-place to keep user_groups clean (Phase 9 drops this table).
        existing.getGroups().clear();
        existing.getAuthorities().clear();
        if (freshUser.getAuthorities() != null) {
            existing.getAuthorities().addAll(freshUser.getAuthorities());
        }
        return save(existing);
    }
}
