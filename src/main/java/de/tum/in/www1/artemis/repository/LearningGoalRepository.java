package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Learning Goal entity.
 */
@Repository
public interface LearningGoalRepository extends JpaRepository<LearningGoal, Long> {

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            WHERE learningGoal.course.id = :#{#courseId}
            ORDER BY learningGoal.title""")
    Set<LearningGoal> findAllByCourseIdWithLectureUnitsUnidirectional(@Param("courseId") Long courseId);

    @Query("""
            SELECT learningGoal
            FROM LearningGoal learningGoal
            LEFT JOIN FETCH learningGoal.lectureUnits lu
            LEFT JOIN FETCH lu.learningGoals
            WHERE learningGoal.id = :#{#learningGoalId}
            """)
    Optional<LearningGoal> findByIdWithLectureUnitsBidirectional(@Param("learningGoalId") long learningGoalId);

    default LearningGoal findByIdWithLectureUnitsBidirectionalElseThrow(long learningGoalId) {
        return findByIdWithLectureUnitsBidirectional(learningGoalId).orElseThrow(() -> new EntityNotFoundException("LearningGoal", learningGoalId));
    }

}
