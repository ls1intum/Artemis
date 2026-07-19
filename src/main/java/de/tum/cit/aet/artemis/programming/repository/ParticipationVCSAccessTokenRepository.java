package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ParticipationVCSAccessToken;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessTokenOverviewDTO;

@Profile(PROFILE_CORE)
@Lazy
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

    default ParticipationVCSAccessToken findByUserIdAndParticipationIdOrElseThrow(long userId, long participationId) {
        return getValueElseThrow(findByUserIdAndParticipationId(userId, participationId));
    }

    default void findByUserIdAndParticipationIdAndThrowIfExists(long userId, long participationId) {
        findByUserIdAndParticipationId(userId, participationId).ifPresent(token -> {
            throw new IllegalStateException();
        });
    }

    /**
     * Deletes the participation token with the given id, but only if it belongs to the given user. Used by the user-settings revoke endpoint so a user can never revoke another
     * user's token.
     *
     * @param id     the id of the token to delete
     * @param userId the id of the user the token must belong to
     * @return the number of deleted rows (0 if no such token exists for that user)
     */
    @Transactional // ok because of delete
    @Modifying
    int deleteByIdAndUserId(long id, long userId);

    /**
     * Returns the participation tokens a user owns as overview projections for the user-settings token overview (metadata only, never the token secret).
     *
     * @param userId the id of the owning user
     * @return the user's participation tokens as overview DTOs
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.VcsAccessTokenOverviewDTO(
                t.id,
                COALESCE(course.title, examCourse.title),
                exercise.title)
            FROM ParticipationVCSAccessToken t
                JOIN t.participation p
                JOIN p.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
            WHERE t.user.id = :userId
            """)
    List<VcsAccessTokenOverviewDTO> findOverviewsByUserId(@Param("userId") long userId);
}
