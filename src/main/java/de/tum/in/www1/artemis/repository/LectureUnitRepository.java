package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Lecture Unit entity.
 */
@Repository
public interface LectureUnitRepository extends JpaRepository<LectureUnit, Long> {

    @Query("""
            SELECT lectureUnit
            FROM LectureUnit lectureUnit
            LEFT JOIN FETCH lectureUnit.learningGoals
            WHERE lectureUnit.id = :#{#lectureUnitId}
            """)
    Optional<LectureUnit> findByIdWithLearningGoals(@Param("lectureUnitId") Long lectureUnitId);

    @Query("""
            SELECT lectureUnit
            FROM LectureUnit lectureUnit
            LEFT JOIN FETCH lectureUnit.learningGoals lg
            LEFT JOIN FETCH lg.lectureUnits
            WHERE lectureUnit.id = :#{#lectureUnitId}
            """)
    Optional<LectureUnit> findByIdWithLearningGoalsBidirectional(@Param("lectureUnitId") long lectureUnitId);

    default LectureUnit findByIdWithLearningGoalsBidirectionalElseThrow(long lectureUnitId) {
        return findByIdWithLearningGoalsBidirectional(lectureUnitId).orElseThrow(() -> new EntityNotFoundException("LectureUnit", lectureUnitId));
    }
}
