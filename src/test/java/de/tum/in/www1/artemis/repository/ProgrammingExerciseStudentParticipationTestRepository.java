package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;

/**
 * Spring Data JPA testing repository for the ProgrammingExerciseStudentParticipation entity.
 */
@Repository
public interface ProgrammingExerciseStudentParticipationTestRepository extends JpaRepository<ProgrammingExerciseStudentParticipation, Long> {

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

}
