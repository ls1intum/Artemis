package de.tum.in.www1.artemis.repository;

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

    @Transactional
    @Modifying
    void deleteAllByLearningGoalId(Long learningGoalId);

    @Transactional
    @Modifying
    void deleteAllByUserId(Long userId);

    @Query("""
            SELECT learningGoalProgress
            FROM LearningGoalProgress learningGoalProgress
            LEFT JOIN FETCH learningGoalProgress.user
            LEFT JOIN FETCH learningGoalProgress.learningGoal
            WHERE learningGoalProgress.learningGoal.id = :learningGoalId
            AND learningGoalProgress.user.id = :userId
            """)
    Optional<LearningGoalProgress> findEagerByLearningGoalIdAndUserId(@Param("learningGoalId") Long learningGoalId, @Param("userId") Long userId);

    @Query("""
            SELECT AVG(learningGoalProgress.confidence)
            FROM LearningGoalProgress learningGoalProgress
            WHERE learningGoalProgress.learningGoal.id = :learningGoalId
            """)
    Optional<Double> findAverageConfidenceByLearningGoalId(@Param("learningGoalId") Long learningGoalId);

}
