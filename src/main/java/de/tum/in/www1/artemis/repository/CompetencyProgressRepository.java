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
    // @Query("DELETE FROM Competency c where c.id = :competencyId")
    void deleteAllByLearningGoalId(Long learningGoalId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(Long userId);

    @Query("""
            SELECT lgp
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
            """)
    List<CompetencyProgress> findAllByLearningGoalId(@Param("competencyId") Long learningGoalId);

    @Query("""
            SELECT lgp
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
                AND lgp.user.id = :userId
            """)
    Optional<CompetencyProgress> findByLearningGoalIdAndUserId(@Param("competencyId") Long learningGoalId, @Param("userId") Long userId);

    @Query("""
            SELECT lgp
            FROM CompetencyProgress lgp
                LEFT JOIN FETCH lgp.user
                LEFT JOIN FETCH lgp.learningGoal
            WHERE lgp.learningGoal.id = :learningGoalId
                AND lgp.user.id = :userId
            """)
    Optional<CompetencyProgress> findEagerByLearningGoalIdAndUserId(@Param("competencyId") Long learningGoalId, @Param("userId") Long userId);

    @Query("""
            SELECT AVG(lgp.confidence)
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
            """)
    Optional<Double> findAverageConfidenceByLearningGoalId(@Param("competencyId") Long learningGoalId);

    @Query("""
            SELECT count(lgp)
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
            """)
    Long countByLearningGoal(@Param("competencyId") Long learningGoalId);

    @Query("""
            SELECT count(lgp)
            FROM CompetencyProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
                AND lgp.progress >= :progress
                AND lgp.confidence >= :confidence
            """)
    Long countByLearningGoalAndProgressAndConfidenceGreaterThanEqual(@Param("competencyId") Long learningGoalId, @Param("progress") Double progress,
            @Param("confidence") Double confidence);

}
