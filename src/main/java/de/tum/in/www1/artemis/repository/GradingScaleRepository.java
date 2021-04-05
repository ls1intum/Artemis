package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA for the GradingScale entity
 */
@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

    Optional<GradingScale> findByCourse_Id(Long courseId);

    Optional<GradingScale> findByExam_Id(Long examId);

    /**
     * Finds a grading scale for course by id or throws an exception if now such grading scale exists
     *
     * @param courseId the course to which the grading scale belongs
     * @return the found grading scale
     */
    @NotNull
    default GradingScale findByCourseIdOrElseThrow(Long courseId) {
        return findByCourse_Id(courseId).orElseThrow(() -> new EntityNotFoundException("Grading scale with course ID " + courseId + " doesn't exist"));
    }

    /**
     * Finds a grading scale for exam by id or throws an exception if now such grading scale exists
     *
     * @param examId the exam to which the grading scale belongs
     * @return the found grading scale
     */
    @NotNull
    default GradingScale findByExamIdOrElseThrow(Long examId) {
        return findByExam_Id(examId).orElseThrow(() -> new EntityNotFoundException("Grading scale with exam ID " + examId + " doesn't exist"));
    }

}
