package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;

/**
 * Spring Data JPA repository for the IrisAssessment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface IrisAssessmentRepository extends ArtemisJpaRepository<IrisAssessment, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "reasoning" })
    Optional<IrisAssessment> findWithReasoningById(long id);

    default IrisAssessment findWithExerciseAndCourseByIdElseThrow(long assessmentId) {
        return getValueElseThrow(findWithExerciseAndCourseById(assessmentId), assessmentId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "exercise", "exercise.course" })
    Optional<IrisAssessment> findWithExerciseAndCourseById(long assessmentId);

    default IrisAssessment findWithReasoningAndExerciseAndCourseByIdElseThrow(long participationId) {
        return getValueElseThrow(findWithReasoningAndExerciseAndCourseById(participationId), participationId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "reasoning", "student", "exercise", "exercise.course" })
    Optional<IrisAssessment> findWithReasoningAndExerciseAndCourseById(long participationId);

    @Query("""
            SELECT COUNT(assessment) > 0
            FROM IrisAssessment assessment
            WHERE assessment.exercise.course.id = :courseId
                AND assessment.verdict = :verdict
                AND assessment.verdictReview IS NULL
            """)
    boolean existsByCourseIdAndVerdictAndVerdictReviewIsNull(@Param("courseId") long courseId, @Param("verdict") IrisVerdict verdict);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            DELETE FROM IrisAssessment assessment
            WHERE assessment.id IN :assessmentIds
            """)
    void deleteAllByIdInBulk(@Param("assessmentIds") Set<Long> assessmentIds);
}
