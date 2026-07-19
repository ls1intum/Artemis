package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;

/**
 * Spring Data JPA repository for the IrisAssessment entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface IrisAssessmentRepository extends ArtemisJpaRepository<IrisAssessment, Long> {

    @EntityGraph(type = LOAD)
    Optional<IrisAssessment> findByExerciseIdAndStudentId(long exerciseId, long studentId);

    @EntityGraph(type = LOAD, attributePaths = { "reasoning" })
    Optional<IrisAssessment> findWithReasoningByExerciseIdAndStudentId(long exerciseId, long studentId);

    @EntityGraph(type = LOAD, attributePaths = { "reasoning" })
    Optional<IrisAssessment> findWithReasoningById(long id);

    default IrisAssessment findWithReasoningAndExerciseAndCourseByIdElseThrow(long participationId) {
        return getValueElseThrow(findWithReasoningAndExerciseAndCourseById(participationId), participationId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "reasoning", "exercise", "exercise.course" })
    Optional<IrisAssessment> findWithReasoningAndExerciseAndCourseById(long participationId);
}
