package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA for the GradingScale entity
 */
@Profile(PROFILE_CORE)
@Repository
public interface GradingScaleRepository extends ArtemisJpaRepository<GradingScale, Long> {

    /**
     * Find a grading scale for course by id
     *
     * @param courseId the courses id
     * @return an Optional with the grading scale if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT gradingScale
            FROM GradingScale gradingScale
            WHERE gradingScale.course.id = :courseId
            """)
    Optional<GradingScale> findByCourseId(@Param("courseId") long courseId);

    /**
     * Find a grading scale for exam by id
     *
     * @param examId the courses id
     * @return an Optional with the grading scale if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT gradingScale
            FROM GradingScale gradingScale
            WHERE gradingScale.exam.id = :examId
            """)
    Optional<GradingScale> findByExamId(@Param("examId") long examId);

    /**
     * Find a grading scale for exam by id with applied bonus
     *
     * @param examId the exam id
     * @return an Optional with the grading scale if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT gradingScale
            FROM GradingScale gradingScale
                LEFT JOIN FETCH gradingScale.bonusFrom
            WHERE gradingScale.exam.id = :examId
            """)
    Optional<GradingScale> findByExamIdWithBonusFrom(@Param("examId") long examId);

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
    default GradingScale findByCourseIdOrElseThrow(long courseId) {
        try {
            return getValueElseThrow(findByCourseId(courseId));
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
    List<GradingScale> findAllByCourseId(long courseId);

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
    default GradingScale findByExamIdOrElseThrow(long examId) {
        try {
            return getValueElseThrow(findByExamId(examId));
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
    List<GradingScale> findAllByExamId(long examId);

    /**
     * Query which fetches all the grading scales with BONUS grade type for which the user is instructor in the course and matching the search criteria.
     *
     * @param partialTitle course or exam title search term
     * @param groups       user groups
     * @param pageable     Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT gs
            FROM GradingScale gs
                LEFT JOIN gs.course
                LEFT JOIN gs.exam
                LEFT JOIN gs.exam.course
            WHERE gs.gradeType = de.tum.cit.aet.artemis.domain.GradeType.BONUS
                AND (
                    (gs.course.instructorGroupName IN :groups AND gs.course.title LIKE %:partialTitle%)
                    OR (gs.exam.course.instructorGroupName IN :groups AND gs.exam.title LIKE %:partialTitle%)
                )
            """)
    // Note: Removing "LEFT JOIN gs.exam.course" part from the query above would cause the query to exclude GradingScales for Courses and just return the
    // GradingScales for Exams. (It will do so by generating a CROSS JOIN and a WHERE clause which checks for exam.course_id = course.id)
    Page<GradingScale> findWithBonusGradeTypeByTitleInCourseOrExamAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("groups") Set<String> groups,
            Pageable pageable);

    /**
     * Same as the linked method except instructor group check is skipped for ADMINs to allow them access to all eligible
     * grading scales.
     *
     * @param partialTitle course or exam title search term
     * @param pageable     Pageable
     * @return Page with search results
     * @see #findWithBonusGradeTypeByTitleInCourseOrExamAndUserHasAccessToCourse(String, Set, Pageable)
     */
    @Query("""
            SELECT gs
            FROM GradingScale gs
                LEFT JOIN gs.course
                LEFT JOIN gs.exam
            WHERE gs.gradeType = de.tum.cit.aet.artemis.domain.GradeType.BONUS
                AND (gs.course.title LIKE %:partialTitle% OR gs.exam.title LIKE %:partialTitle%)
            """)
    Page<GradingScale> findWithBonusGradeTypeByTitleInCourseOrExamForAdmin(@Param("partialTitle") String partialTitle, Pageable pageable);

    /**
     * Find grading scales for courses
     *
     * @param courseIds the courses to which the grading scales belong
     * @return a set of grading scales for the courses
     */
    @Query("""
            SELECT gs
            FROM GradingScale gs
            WHERE gs.course.id IN :courseIds
            """)
    Set<GradingScale> findAllByCourseIds(@Param("courseIds") Set<Long> courseIds);

    @EntityGraph(type = LOAD, attributePaths = "bonusFrom")
    Optional<GradingScale> findWithEagerBonusFromByBonusFromId(long bonusId);

    @EntityGraph(type = LOAD, attributePaths = "bonusFrom")
    Optional<GradingScale> findWithEagerBonusFromByExamId(long examId);

    /**
     * Maps a grade percentage to a valid grade step within the grading scale or throws an exception if no match was found
     *
     * @param percentage     the grade percentage to be mapped
     * @param gradingScaleId the identifier for the grading scale
     * @return grade step corresponding to the given percentage
     */
    default GradeStep matchPercentageToGradeStep(double percentage, long gradingScaleId) {
        Set<GradeStep> gradeSteps = findByIdElseThrow(gradingScaleId).getGradeSteps();
        return this.matchPercentageToGradeStep(percentage, gradeSteps);
    }

    /**
     * @param percentage the grade percentage to be mapped
     * @param gradeSteps the grade steps of a grading scale
     * @return grade step corresponding to the given percentage
     * @see #matchPercentageToGradeStep(double, long)
     */
    private GradeStep matchPercentageToGradeStep(double percentage, Set<GradeStep> gradeSteps) {
        if (percentage < 0) {
            throw new BadRequestAlertException("Grade percentages must be greater than 0", "gradeStep", "invalidGradePercentage");
        }
        Optional<GradeStep> matchingGradeStep = gradeSteps.stream().filter(gradeStep -> gradeStep.matchingGradePercentage(percentage)).findFirst();
        if (matchingGradeStep.isPresent()) {
            return matchingGradeStep.get();
        }
        if (percentage > 100) {
            // return the highest grade step for percentages > 100 (bonus points)
            return getArbitraryValueElseThrow(gradeSteps.stream().max(Comparator.comparing(GradeStep::getUpperBoundPercentage)));
        }
        throw new EntityNotFoundException("No grade step in selected grading scale matches given percentage");
    }

    /**
     * Deletes all excessive grading scales but the first saved for a course/exam
     *
     * @param entityId the id of the course/exam
     * @param isExam   determines if the method is handling a grading scale for course or exam
     * @return the only remaining grading scale for the course/exam
     */
    default GradingScale deleteExcessiveGradingScales(long entityId, boolean isExam) {
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
        return gradingScales.getFirst();
    }
}
