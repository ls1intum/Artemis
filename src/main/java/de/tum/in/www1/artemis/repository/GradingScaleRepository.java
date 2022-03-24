package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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
     * Finds a grading scale for course by id or throws an exception if no such grading scale exists.
     * If there is more the one grading scale for the course, all but the first one saved will get deleted
     * and the first one saved will be returned. This is necessary to avoid potential concurrency issues
     * since only one grading scale can exist for a course at a time.
     *
     * @param courseId the course to which the grading scale belongs
     * @return the found grading scale
     */
    @NotNull
    default GradingScale findByCourseIdOrElseThrow(Long courseId) {
        try {
            return findByCourseId(courseId).orElseThrow(() -> new EntityNotFoundException("Grading scale with course ID " + courseId + " doesn't exist"));
        }
        catch (IncorrectResultSizeDataAccessException exception) {
            return deleteExcessiveGradingScales(courseId, false);
        }
    }

    /**
     * Find all grading scales for a course
     *
     * @param courseId the id of the course
     * @return a list of grading scales for the course
     */
    List<GradingScale> findAllByCourseId(@Param("courseId") Long courseId);

    /**
     * Finds a grading scale for exam by id or throws an exception if no such grading scale exists.
     * If there is more the one grading scale for the exam, all but the first one saved will get deleted
     * and the first one saved will be returned. This is necessary to avoid potential concurrency issues
     * since only one grading scale can exist for an exam at a time.
     *
     * @param examId the exam to which the grading scale belongs
     * @return the found grading scale
     */
    @NotNull
    default GradingScale findByExamIdOrElseThrow(Long examId) {
        try {
            return findByExamId(examId).orElseThrow(() -> new EntityNotFoundException("Grading scale with exam ID " + examId + " doesn't exist"));
        }
        catch (IncorrectResultSizeDataAccessException exception) {
            return deleteExcessiveGradingScales(examId, true);
        }
    }

    /**
     * Find all grading scales for an exam
     *
     * @param examId the id of the exam
     * @return a list of grading scales for the exam
     */
    List<GradingScale> findAllByExamId(@Param("examId") Long examId);

    /**
     * Maps a grade percentage to a valid grade step within the grading scale or throws an exception if no match was found
     *
     * @param percentage the grade percentage to be mapped
     * @param gradingScaleId the identifier for the grading scale
     * @return grade step corresponding to the given percentage
     */
    default GradeStep matchPercentageToGradeStep(double percentage, Long gradingScaleId) {
        if (percentage < 0 || percentage > 100) {
            throw new BadRequestAlertException("Grade percentages must be between 0 and 100", "gradeStep", "invalidGradePercentage");
        }
        Set<GradeStep> gradeSteps = findById(gradingScaleId).get().getGradeSteps();
        Optional<GradeStep> matchingGradeStep = gradeSteps.stream().filter(gradeStep -> gradeStep.matchingGradePercentage(percentage)).findFirst();
        if (matchingGradeStep.isPresent()) {
            return matchingGradeStep.get();
        }
        else {
            throw new EntityNotFoundException("No grade step in selected grading scale matches given percentage");
        }
    }

    /**
     * Deletes all excessive grading scales but the first saved for a course/exam
     *
     * @param entityId the id of the course/exam
     * @param isExam determines if the method is handling a grading scale for course or exam
     * @return the only remaining grading scale for the course/exam
     */
    default GradingScale deleteExcessiveGradingScales(Long entityId, boolean isExam) {
        List<GradingScale> gradingScales;
        if (isExam) {
            gradingScales = findAllByExamId(entityId);
        }
        else {
            gradingScales = findAllByCourseId(entityId);
        }
        for (int i = 1; i < gradingScales.size(); i++) {
            deleteById(gradingScales.get(i).getId());
        }
        return gradingScales.get(0);
    }
}
