package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Bonus;

/**
 * Spring Data JPA for the BonusSource entity
 */
@Repository
public interface BonusRepository extends JpaRepository<Bonus, Long> {

    /**
     * Find a bonus source by its source grading scale id
     *
     * @param sourceGradindScaleId the source grading scale id
     * @return an Optional with the bonus source if such scale exists and an empty Optional otherwise
     */
    Optional<Bonus> findBySourceGradingScaleId(@Param("sourceGradindScaleId") Long sourceGradindScaleId);

    /**
     * Find a bonus source with its source grading scale belonging to the course with given id
     *
     * @param courseId the courses id
     * @return an Optional with the bonus source if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT bonus
            FROM Bonus bonus
            WHERE bonus.sourceGradingScale.course.id = :#{#courseId}
            """)
    Optional<Bonus> findBySourceCourseId(@Param("courseId") Long courseId);

    /**
     * Find a bonus source with its source grading scale belonging to the exam with given id
     *
     * @param examId the courses id
     * @return an Optional with the bonus source if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT bonus
            FROM Bonus bonus
            WHERE bonus.sourceGradingScale.exam.id = :#{#examId}
            """)
    Optional<Bonus> findBySourceExamId(@Param("examId") Long examId);

    @Query("""
            SELECT gs.bonusFrom
            FROM GradingScale gs
            WHERE gs.exam.id = :#{#examId}
            """)
    Set<Bonus> findAllByTargetExamId(@Param("examId") Long examId);

}
