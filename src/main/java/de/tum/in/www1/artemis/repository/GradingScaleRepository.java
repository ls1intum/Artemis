package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA for the GradingScale entity
 */
@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

    /**
     * Find a grading scale for course by id
     *
     * @param courseId the courses id
     * @return an Optional with the grading scale if such scale exists and an empty Optional otherwise
     */
    @Query("""
                SELECT gradingScale
                FROM GradingScale gradingScale
                WHERE gradingScale.course.id = :#{#courseId}
            """)
    Optional<GradingScale> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Find a grading scale for exam by id
     *
     * @param examId the courses id
     * @return an Optional with the grading scale if such scale exists and an empty Optional otherwise
     */
    @Query("""
                SELECT gradingScale
                FROM GradingScale gradingScale
                WHERE gradingScale.exam.id = :#{#examId}
            """)
    Optional<GradingScale> findByExamId(@Param("examId") Long examId);

    /**
     * Finds a grading scale for course by id or throws an exception if now such grading scale exists
     *
     * @param courseId the course to which the grading scale belongs
     * @return the found grading scale
     */
    @NotNull
    default GradingScale findByCourseIdOrElseThrow(Long courseId) {
        return findByCourseId(courseId).orElseThrow(() -> new EntityNotFoundException("Grading scale with course ID " + courseId + " doesn't exist"));
    }

    /**
     * Finds a grading scale for exam by id or throws an exception if now such grading scale exists
     *
     * @param examId the exam to which the grading scale belongs
     * @return the found grading scale
     */
    @NotNull
    default GradingScale findByExamIdOrElseThrow(Long examId) {
        return findByExamId(examId).orElseThrow(() -> new EntityNotFoundException("Grading scale with exam ID " + examId + " doesn't exist"));
    }

}
