package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.LearningGoal;

/**
 * Spring Data JPA repository for the LearningGoal entity.
 */
public interface LearningGoalRepository extends JpaRepository<LearningGoal, Long> {

    @Query("""
               SELECT learningGoal
               FROM LearningGoal learningGoal
                    LEFT JOIN FETCH learningGoal.course
                    LEFT JOIN FETCH learningGoal.exercises
                    LEFT JOIN FETCH learningGoal.lectures
               WHERE learningGoal.course.id = :#{#courseId}
            """)
    Set<LearningGoal> findAllByCourseId(@Param("courseId") Long courseId);

}
