package de.tum.cit.aet.artemis.account.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     * Batch-loads users with their {@code authorities} eagerly for the given set of logins.
     * Used by {@link #saveAll} to fetch all existing users in a single query instead of
     * issuing one {@code findOneWithAuthoritiesByLogin} query per user.
     * <p>
     * Unlike the production {@code findAllWithAuthoritiesByDeletedIsFalseAndLoginIn}, this
     * variant does NOT filter by {@code deleted = FALSE} — test users may be soft-deleted but
     * still need to be updated in-place to avoid the Hibernate {@code PersistentSet(sn=null)} NPE.
     *
     * @param logins the set of logins to load
     * @return users with eagerly initialised {@code authorities}
     */
    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    @Query("SELECT u FROM User u WHERE u.login IN :logins")
    Set<User> findAllWithAuthoritiesByLoginIn(@Param("logins") Set<String> logins);

    @Override
    @Transactional
    default User save(User freshUser) {
        if (freshUser.getId() == null) {
            // New user — plain persist(), no collections to worry about.
            return UserRepository.super.save(freshUser);
        }
        // Load with authorities eagerly so the PersistentSet sn is initialized (prevents NPE at flush).
        User existing = findOneWithAuthoritiesByLogin(freshUser.getLogin()).orElseThrow(() -> new IllegalStateException("User not found for update: " + freshUser.getLogin()));
        copyFieldsInPlace(freshUser, existing);
        return UserRepository.super.save(existing);
    }

    /**
     * Overrides {@code saveAll} to prevent the Hibernate {@code PersistentSet(sn=null)} NPE
     * for batch saves, using a single batch query instead of one query per entity.
     * <p>
     * New users (no ID) are passed directly to {@code persist()}. Existing users are
     * batch-loaded with {@code authorities} eagerly via {@link #findAllWithAuthoritiesByLoginIn}
     * (one query for the whole batch), then each is updated in-place via {@link #copyFieldsInPlace}
     * so the {@code PersistentSet} snapshot is always initialised before flush.
     */
    @Override
    @Transactional
    default List<User> saveAll(Iterable<? extends User> entities) {
        List<User> all = new ArrayList<>();
        entities.forEach(all::add);

        // Collect logins of users that already have a DB row (id != null).
        Set<String> existingLogins = all.stream().filter(u -> u.getId() != null).map(User::getLogin).collect(Collectors.toSet());

        // One batch query to load them all with authorities eagerly (initialises PersistentSet sn).
        Map<String, User> loadedByLogin = existingLogins.isEmpty() ? Map.of()
                : findAllWithAuthoritiesByLoginIn(existingLogins).stream().collect(Collectors.toMap(User::getLogin, u -> u));

        List<User> result = new ArrayList<>();
        for (User fresh : all) {
            User loaded = fresh.getId() != null ? loadedByLogin.get(fresh.getLogin()) : null;
            if (loaded == null) {
                // New user (or not found) — plain persist(), no PersistentSet to worry about.
                result.add(UserRepository.super.save(fresh));
            }
            else {
                copyFieldsInPlace(fresh, loaded);
                result.add(UserRepository.super.save(loaded));
            }
        }
        return result;
    }

    /**
     * Overrides the default {@code save} to prevent a {@code NullPointerException} in Hibernate's
     * {@code sortCollectionActions} when saving a detached {@link User} that already exists in the DB.
     * <p>
     * When {@code save(freshUser)} is called on a User with an existing ID, Spring Data JPA delegates
     * to JPA {@code merge()}. Hibernate loads the managed entity with lazy, uninitialized
     * {@code PersistentSet} for the {@code authorities} {@code @ManyToMany} collection ({@code sn = null}).
     * Replacing the field with a plain Java {@code Set} then queues a {@code CollectionUpdateAction},
     * whose {@code compareTo} calls {@code hasDeletes()} on the uninitialized snapshot → NPE.
     * <p>
     * This override avoids that by loading the managed user <strong>with authorities eagerly</strong>
     * first (within the same {@code @Transactional} scope so they share the Hibernate session),
     * then updating the {@code authorities} {@code PersistentSet} in-place rather than replacing it.
     * New users (no ID) are delegated directly to the default implementation.
     *
     * @param freshUser the user to persist (new) or update (existing); must have all fields set
     * @return the saved or updated user
     */
    /**
     * Copies all scalar fields and updates the {@code authorities} collection in-place from
     * {@code fresh} into {@code existing}. Never replaces the {@code PersistentSet} reference so
     * the Hibernate snapshot ({@code sn}) stays initialised and no NPE occurs at flush time.
     */
    private void copyFieldsInPlace(User fresh, User existing) {
        existing.setPassword(fresh.getPassword());
        existing.setFirstName(fresh.getFirstName());
        existing.setLastName(fresh.getLastName());
        existing.setEmail(fresh.getEmail());
        existing.setActivated(fresh.getActivated());
        existing.setDeleted(fresh.isDeleted());
        existing.setLangKey(fresh.getLangKey());
        existing.setRegistrationNumber(fresh.getRegistrationNumber());
        existing.setVcsAccessToken(fresh.getVcsAccessToken());
        existing.setVcsAccessTokenExpiryDate(fresh.getVcsAccessTokenExpiryDate());
        existing.setSelectedLLMUsageTimestamp(fresh.getSelectedLLMUsageTimestamp());
        existing.setSelectedLLMUsage(fresh.getSelectedLLMUsage());
        existing.getAuthorities().clear();
        if (fresh.getAuthorities() != null) {
            existing.getAuthorities().addAll(fresh.getAuthorities());
        }
    }
}
