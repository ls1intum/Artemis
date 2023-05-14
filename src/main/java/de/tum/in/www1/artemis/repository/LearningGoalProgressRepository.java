package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.LearningGoalProgress;

@Repository
public interface LearningGoalProgressRepository extends JpaRepository<LearningGoalProgress, Long> {

    @Transactional // ok because of delete
    @Modifying
    // @Query("DELETE FROM Competency c where c.id = :competencyId")
    void deleteAllByLearningGoalId(Long learningGoalId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(Long userId);

    @Query("""
            SELECT lgp
            FROM LearningGoalProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
            """)
    List<LearningGoalProgress> findAllByLearningGoalId(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT lgp
            FROM LearningGoalProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
                AND lgp.user.id = :userId
            """)
    Optional<LearningGoalProgress> findByLearningGoalIdAndUserId(@Param("learningGoalId") Long learningGoalId, @Param("userId") Long userId);

    @Query("""
            SELECT lgp
            FROM LearningGoalProgress lgp
                LEFT JOIN FETCH lgp.user
                LEFT JOIN FETCH lgp.learningGoal
            WHERE lgp.learningGoal.id = :learningGoalId
                AND lgp.user.id = :userId
            """)
    Optional<LearningGoalProgress> findEagerByLearningGoalIdAndUserId(@Param("learningGoalId") Long learningGoalId, @Param("userId") Long userId);

    @Query("""
            SELECT AVG(lgp.confidence)
            FROM LearningGoalProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
            """)
    Optional<Double> findAverageConfidenceByLearningGoalId(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT count(lgp)
            FROM LearningGoalProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
            """)
    Long countByLearningGoal(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT count(lgp)
            FROM LearningGoalProgress lgp
            WHERE lgp.learningGoal.id = :learningGoalId
                AND lgp.progress >= :progress
                AND lgp.confidence >= :confidence
            """)
    Long countByLearningGoalAndProgressAndConfidenceGreaterThanEqual(@Param("learningGoalId") Long learningGoalId, @Param("progress") Double progress,
            @Param("confidence") Double confidence);

}
