package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

    Optional<GradingScale> findByCourse_Id(Long courseId);

    Optional<GradingScale> findByExam_Id(Long examId);

    @NotNull
    default GradingScale findByCourseIdOrElseThrow(Long courseId) {
        return findByCourse_Id(courseId).orElseThrow(() -> new EntityNotFoundException("Grading scale with course ID " + courseId + " doesn't exist"));
    }

    @NotNull
    default GradingScale findByExamIdOrElseThrow(Long examId) {
        return findByExam_Id(examId).orElseThrow(() -> new EntityNotFoundException("Grading scale with exam ID " + examId + " doesn't exist"));
    }

}
