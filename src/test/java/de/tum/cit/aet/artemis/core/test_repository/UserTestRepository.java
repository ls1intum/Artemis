package de.tum.cit.aet.artemis.core.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Repository
@Primary
public interface UserTestRepository extends UserRepository {

    Set<User> findAllByGroupsNotEmpty();

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalse();

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

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findOneWithLearningPathsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findWithLearningPathsById(long userId);

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
}
