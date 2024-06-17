package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Repository
public interface CompetencyProgressRepository extends JpaRepository<CompetencyProgress, Long> {

    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
            """)
    void deleteAllByCompetencyId(@Param("competencyId") long competencyId);

    List<CompetencyProgress> findAllByCompetencyId(long competencyId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
                AND cp.user.id = :userId
            """)
    Optional<CompetencyProgress> findByCompetencyIdAndUserId(@Param("competencyId") long competencyId, @Param("userId") long userId);

    default CompetencyProgress findByCompetencyIdAndUserIdOrElseThrow(long competencyId, long userId) {
        return findByCompetencyIdAndUserId(competencyId, userId).orElseThrow(() -> new EntityNotFoundException("CompetencyProgress"));
    }

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
                LEFT JOIN cp.competency
            WHERE cp.competency IN :competencies
                AND cp.user.id = :userId
            """)
    Set<CompetencyProgress> findByCompetenciesAndUser(@Param("competencies") Collection<Competency> competencies, @Param("userId") long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
                LEFT JOIN FETCH cp.user
                LEFT JOIN FETCH cp.competency
            WHERE cp.competency.id = :competencyId
                AND cp.user.id = :userId
            """)
    Optional<CompetencyProgress> findEagerByCompetencyIdAndUserId(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.competency.id IN :competencyIds
                AND cp.user.id = :userId
            """)
    Set<CompetencyProgress> findAllByCompetencyIdsAndUserId(@Param("competencyIds") Set<Long> competencyIds, @Param("userId") long userId);

    @Query("""
            SELECT AVG(cp.confidence)
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
            """)
    Optional<Double> findAverageConfidenceByCompetencyId(@Param("competencyId") long competencyId);

    @Query("""
            SELECT COUNT(cp)
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
            """)
    Long countByCompetency(@Param("competencyId") long competencyId);

    @Query("""
            SELECT COUNT(cp)
            FROM CompetencyProgress cp
            WHERE cp.competency.id = :competencyId
                AND cp.progress >= :progress
                AND cp.confidence >= :confidence
            """)
    Long countByCompetencyAndProgressAndConfidenceGreaterThanEqual(@Param("competencyId") long competencyId, @Param("progress") double progress,
            @Param("confidence") double confidence);

    @Query("""
            SELECT cp
            FROM Competency c
                LEFT JOIN CompetencyRelation cr ON cr.tailCompetency = c
                LEFT JOIN Competency priorC ON priorC = cr.headCompetency
                LEFT JOIN FETCH CompetencyProgress cp ON cp.competency = priorC
            WHERE cr.type <> de.tum.in.www1.artemis.domain.competency.RelationType.MATCHES
                AND cp.user = :userId
                AND c = :competencyId
            """)
    Set<CompetencyProgress> findAllPriorByCompetencyId(@Param("competency") Competency competency, @Param("user") User userId);
}
