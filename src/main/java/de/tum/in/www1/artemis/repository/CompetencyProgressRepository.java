package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.CompetencyProgress;

@Repository
public interface CompetencyProgressRepository extends JpaRepository<CompetencyProgress, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByLearningGoalId(Long learningGoalId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(Long userId);

    @Query("""
            SELECT lgp
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :competencyId
            """)
    List<CompetencyProgress> findAllByCompetencyId(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT lgp
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :competencyId
                AND lgp.user.id = :userId
            """)
    Optional<CompetencyProgress> findByCompetencyIdAndUserId(@Param("competencyId") Long competencyId, @Param("userId") Long userId);

    @Query("""
            SELECT lgp
            FROM CompetencyProgress lgp
                LEFT JOIN FETCH lgp.user
                LEFT JOIN FETCH lgp.learningGoal
            WHERE lgp.learningGoal.id = :competencyId
                AND lgp.user.id = :userId
            """)
    Optional<CompetencyProgress> findEagerByCompetencyIdAndUserId(@Param("competencyId") Long competencyId, @Param("userId") Long userId);

    @Query("""
            SELECT AVG(lgp.confidence)
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :competencyId
            """)
    Optional<Double> findAverageConfidenceByCompetencyId(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT count(lgp)
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :competencyId
            """)
    Long countByCompetency(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT count(lgp)
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :competencyId
                AND lgp.progress >= :progress
                AND lgp.confidence >= :confidence
            """)
    Long countByCompetencyAndProgressAndConfidenceGreaterThanEqual(@Param("competencyId") Long competencyId, @Param("progress") Double progress,
            @Param("confidence") Double confidence);

}
