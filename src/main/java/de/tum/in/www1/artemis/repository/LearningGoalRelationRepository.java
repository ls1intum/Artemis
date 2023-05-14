package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LearningGoalRelation;

/**
 * Spring Data JPA repository for the Learning Goal Relation entity.
 */
@Repository
public interface LearningGoalRelationRepository extends JpaRepository<LearningGoalRelation, Long> {

    @Query("""
            SELECT relation
            FROM LearningGoalRelation relation
            WHERE relation.headCompetency.id = :#{#learningGoalId}
            OR relation.tailCompetency.id = :#{#learningGoalId}
            """)
    Set<LearningGoalRelation> findAllByLearningGoalId(@Param("learningGoalId") Long learningGoalId);

    @Query("""
            SELECT relation
            FROM LearningGoalRelation relation
            LEFT JOIN FETCH relation.headCompetency
            LEFT JOIN FETCH relation.tailCompetency
            WHERE relation.headCompetency.course.id = :#{#courseId}
            AND relation.tailCompetency.course.id = :#{#courseId}
            """)
    Set<LearningGoalRelation> findAllByCourseId(@Param("courseId") Long courseId);

}
