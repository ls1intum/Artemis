package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface RepositoryVCSAccessTokenRepository extends ArtemisJpaRepository<RepositoryVCSAccessToken, Long> {

    /**
     * Finds the repository-scoped VCS access token a user owns for a specific base repository (identified by its canonical URI).
     *
     * @param userId        the id of the owning user
     * @param repositoryUri the canonical repository URI the token is bound to
     * @return an {@link Optional} containing the token if it exists
     */
    Optional<RepositoryVCSAccessToken> findByUserIdAndRepositoryUri(long userId, String repositoryUri);

    /**
     * Returns the repository URIs a user already has a token for, restricted to the given exercises. Used to batch-create only the missing tokens.
     *
     * @param userId      the id of the owning user
     * @param exerciseIds the ids of the exercises to restrict the lookup to
     * @return the set of repository URIs the user already has a token for
     */
    @Query("""
            SELECT t.repositoryUri
            FROM RepositoryVCSAccessToken t
            WHERE t.user.id = :userId
                AND t.exercise.id IN :exerciseIds
            """)
    Set<String> findRepositoryUrisByUserIdAndExerciseIdIn(@Param("userId") long userId, @Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Returns the ids of the users that already have a token for the given repository URI. Used to batch-create only the missing tokens when adding a repository.
     *
     * @param repositoryUri the canonical repository URI
     * @return the ids of the users that already own a token for this repository
     */
    @Query("""
            SELECT t.user.id
            FROM RepositoryVCSAccessToken t
            WHERE t.repositoryUri = :repositoryUri
            """)
    Set<Long> findUserIdsByRepositoryUri(@Param("repositoryUri") String repositoryUri);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByExerciseId(long exerciseId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(long userId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserIdAndExerciseIdIn(long userId, Collection<Long> exerciseIds);
}
