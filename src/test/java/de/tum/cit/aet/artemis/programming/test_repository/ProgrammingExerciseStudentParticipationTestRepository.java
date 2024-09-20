package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.List;

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
    void updateBuildPlanIdOfAll(@Param("buildPlanId") Long buildPlanId);

    List<ProgrammingExerciseStudentParticipation> findByExerciseId(long exerciseId);
}
