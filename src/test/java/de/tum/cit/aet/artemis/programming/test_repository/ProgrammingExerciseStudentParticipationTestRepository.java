package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationProjectionDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Spring Data JPA testing repository for the ProgrammingExerciseStudentParticipation entity.
 */
@Lazy
@Repository
@Primary
public interface ProgrammingExerciseStudentParticipationTestRepository extends ProgrammingExerciseStudentParticipationRepository {

    /**
     * updates the build plan id of all programming exercise student participations
     *
     * @param buildPlanId new build plan id to be set
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseStudentParticipation p
            SET p.buildPlanId = :buildPlanId
            """)
    void updateBuildPlanIdOfAll(@Param("buildPlanId") String buildPlanId);

    /**
     * Finds Iris assessment participation projections for participations whose latest result has a positive score.
     *
     * @param exerciseId the exercise id
     * @return matching participation projections
     */
    default Set<IrisAssessmentProgrammingStudentParticipationProjectionDTO> findAllNonPracticeIrisAssessmentParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(
            long exerciseId) {
        var participationIds = findParticipationIdsWithLatestResultScoreGreaterThanZeroAndNotPractice(exerciseId);
        if (participationIds.isEmpty()) {
            return Set.of();
        }
        return findAllIrisAssessmentParticipationProjectionsByIdIn(participationIds);
    }
}
