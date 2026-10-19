package de.tum.cit.aet.artemis.programming.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
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
     * Only tests look a participation up by the student's login: the server always has the student's id by the time it
     * needs one, and selecting on the joined user row makes the database read every participation of the exercise.
     *
     * @param exerciseId the exercise the participation belongs to
     * @param username   the student's login
     * @return the student's participation, with its submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(long exerciseId, String username);

    /**
     * @param exerciseId the exercise the participations belong to
     * @param username   the student's login
     * @return every participation the student has in that exercise, graded and practice
     */
    List<ProgrammingExerciseStudentParticipation> findAllByExerciseIdAndStudentLogin(long exerciseId, String username);

    /**
     * @param exerciseId the exercise the participation belongs to
     * @param teamId     the team the participation belongs to
     * @return the team's participation, with its members
     */
    @EntityGraph(type = LOAD, attributePaths = { "team.students" })
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(long exerciseId, long teamId);

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
