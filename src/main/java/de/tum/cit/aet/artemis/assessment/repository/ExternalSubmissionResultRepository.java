package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/** Read operations that decide which students can still receive an external submission. */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExternalSubmissionResultRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Finds which of the given users already have a result in an exercise, either through their own participation or through the participation of a team they belong to.
     * Practice participations (test runs) are ignored, like everywhere a student's result for the exercise is looked up.
     *
     * @param exerciseId the id of the exercise
     * @param userIds    the ids of the users to check
     * @return the ids of the given users that have at least one result in the exercise
     */
    @Query("""
            SELECT DISTINCT u.id
            FROM StudentParticipation p
                JOIN p.submissions s
                JOIN s.results r
                LEFT JOIN p.student st
                LEFT JOIN p.team t
                LEFT JOIN t.students ts
                JOIN User u ON u.id = st.id OR u.id = ts.id
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND u.id IN :userIds
            """)
    Set<Long> findParticipantIdsWithResultForExercise(@Param("exerciseId") long exerciseId, @Param("userIds") Collection<Long> userIds);
}
