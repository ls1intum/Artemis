package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.participation.ParticipationVCSAccessToken;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface ParticipationVCSAccessTokenRepository extends ArtemisJpaRepository<ParticipationVCSAccessToken, Long> {

    /**
     * Delete all participation vcs access token that belong to the given participation
     *
     * @param participationId the id of the participation where the tokens should be deleted
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteByParticipationId(long participationId);

    /**
     * Delete all tokens of a user
     *
     * @param userId The id of the user
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(long userId);

    @Query("""
            SELECT DISTINCT p
            FROM ParticipationVCSAccessToken p
                 LEFT JOIN FETCH p.participation
                 LEFT JOIN FETCH p.user
            WHERE p.user.id = :userId AND p.participation.id = :participationId
            """)
    Optional<ParticipationVCSAccessToken> findByUserIdAndParticipationId(@Param("userId") long userId, @Param("participationId") long participationId);

    default ParticipationVCSAccessToken findByUserIdAndParticipationIdOrElseThrow(Long userId, Long participationId) {
        return getValueElseThrow(findByUserIdAndParticipationId(userId, participationId));
    }

    default void findByUserIdAndParticipationIdAndThrowIfExists(Long userId, Long participationId) {
        findByUserIdAndParticipationId(userId, participationId).ifPresent(token -> {
            throw new IllegalStateException();
        });
    }
}
