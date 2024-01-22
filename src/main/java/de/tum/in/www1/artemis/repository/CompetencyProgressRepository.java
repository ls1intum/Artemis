package de.tum.in.www1.artemis.repository;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;

@Repository
public interface CompetencyProgressRepository extends JpaRepository<CompetencyProgress, Long> {

    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM CompetencyProgress cp WHERE cp.learningGoal.id = :learningGoalId")
    void deleteAllByCompetencyId(Long learningGoalId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(Long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.learningGoal.id = :competencyId
            """)
    List<CompetencyProgress> findAllByCompetencyId(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.learningGoal.id = :competencyId
                AND cp.user.id = :userId
            """)
    Optional<CompetencyProgress> findByCompetencyIdAndUserId(@Param("competencyId") Long competencyId, @Param("userId") Long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
                LEFT JOIN cp.learningGoal
            WHERE cp.learningGoal in :competencies
                AND cp.user.id = :userId
            """)
    Set<CompetencyProgress> findByCompetenciesAndUser(@Param("competencies") Collection<Competency> competencies, @Param("userId") Long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
                LEFT JOIN FETCH cp.user
                LEFT JOIN FETCH cp.learningGoal
            WHERE cp.learningGoal.id = :competencyId
                AND cp.user.id = :userId
            """)
    Optional<CompetencyProgress> findEagerByCompetencyIdAndUserId(@Param("competencyId") Long competencyId, @Param("userId") Long userId);

    @Query("""
            SELECT cp
            FROM CompetencyProgress cp
            WHERE cp.learningGoal.id IN :competencyIds
                AND cp.user.id = :userId
            """)
    Set<CompetencyProgress> findAllByCompetencyIdsAndUserId(@Param("competencyIds") Set<Long> competencyIds, @Param("userId") long userId);

    @Query("""
            SELECT AVG(cp.confidence)
            FROM CompetencyProgress cp
            WHERE cp.learningGoal.id = :competencyId
            """)
    Optional<Double> findAverageConfidenceByCompetencyId(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT count(cp)
            FROM CompetencyProgress cp
            WHERE cp.learningGoal.id = :competencyId
            """)
    Long countByCompetency(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT count(cp)
            FROM CompetencyProgress cp
            WHERE cp.learningGoal.id = :competencyId
                AND cp.progress >= :progress
                AND cp.confidence >= :confidence
            """)
    Long countByCompetencyAndProgressAndConfidenceGreaterThanEqual(@Param("competencyId") Long competencyId, @Param("progress") Double progress,
            @Param("confidence") Double confidence);

}
