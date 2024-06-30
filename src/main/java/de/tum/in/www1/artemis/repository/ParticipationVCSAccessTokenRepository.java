package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.ParticipationVCSAccessToken;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface ParticipationVCSAccessTokenRepository extends ArtemisJpaRepository<ParticipationVCSAccessToken, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM ParticipationVCSAccessToken p
                LEFT JOIN FETCH p.participation
                LEFT JOIN FETCH p.user
            WHERE p.id = :participationVCSAccessTokenId
            """)
    Optional<ParticipationVCSAccessToken> findByIdWithParticipationAndUser(@Param("participationVCSAccessTokenId") long participationVCSAccessTokenId);

    @Query("""
            SELECT DISTINCT p
            FROM ParticipationVCSAccessToken p
                LEFT JOIN FETCH p.participation
                Left JOIN FETCH p.user
            WHERE p.user.id = :userId
            """)
    List<ParticipationVCSAccessToken> findByUserIdWithEagerParticipation(@Param("userId") Long userId);

    @Query("""
                   SELECT p
                   FROM ParticipationVCSAccessToken p
                   WHERE p.user.id = :userId AND p.participation.id = :participationId
            """)
    Optional<ParticipationVCSAccessToken> findByUserIdAndParticipationId(@Param("userId") Long userId, @Param("participationId") Long participationId);

    @Query("""
            SELECT DISTINCT p
            FROM ParticipationVCSAccessToken p
            WHERE p.user.id = :userId
            """)
    List<ParticipationVCSAccessToken> findByUserId(@Param("userId") Long userId);
}
