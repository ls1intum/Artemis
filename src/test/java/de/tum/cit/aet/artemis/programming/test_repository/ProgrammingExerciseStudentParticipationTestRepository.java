package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
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
     * Returns the participations matching the given ids within the given exercise, with all of their submissions.
     * <p>
     * Production code fetches only the newest submission, because that is all a build trigger reads. Test setup helpers
     * want the whole collection, so the wider fetch lives here.
     *
     * @param exerciseId       is used as a filter for the found participations
     * @param participationIds the participations to retrieve
     * @return the matching participations with all their submissions
     */
    @Query("""
            SELECT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
            WHERE participation.exercise.id = :exerciseId
                AND participation.id IN :participationIds
            """)
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndParticipationIds(@Param("exerciseId") long exerciseId,
            @Param("participationIds") Collection<Long> participationIds);
}
