package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseStudentParticipationRepository extends ArtemisJpaRepository<ProgrammingExerciseStudentParticipation, Long> {

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.results pr
                LEFT JOIN FETCH pr.feedbacks f
                LEFT JOIN FETCH f.testCase
                LEFT JOIN FETCH pr.submission
            WHERE p.id = :participationId
                AND (pr.id = (
                    SELECT MAX(prr.id)
                    FROM p.results prr
                    WHERE (prr.assessmentType = de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.AUTOMATIC
                        OR (prr.completionDate IS NOT NULL
                            AND (p.exercise.assessmentDueDate IS NULL OR p.exercise.assessmentDueDate < :dateTime)
                        )
                    )
                ) OR pr.id IS NULL)
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(@Param("participationId") long participationId,
            @Param("dateTime") ZonedDateTime dateTime);

    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.results pr
                LEFT JOIN FETCH p.submissions
            WHERE p.id = :participationId AND ((pr.assessmentType = 'AUTOMATIC'
                        OR (pr.completionDate IS NOT NULL
                            AND (p.exercise.assessmentDueDate IS NULL
                                OR p.exercise.assessmentDueDate < :#{#dateTime}))) OR pr.id IS NULL)
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithAllResultsAndRelatedSubmissions(@Param("participationId") long participationId,
            @Param("dateTime") ZonedDateTime dateTime);

    @EntityGraph(type = LOAD, attributePaths = { "results", "exercise", "team.students" })
    List<ProgrammingExerciseStudentParticipation> findWithResultsAndExerciseAndTeamStudentsByBuildPlanId(String buildPlanId);

    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.results
            WHERE p.buildPlanId IS NOT NULL
                AND (p.student IS NOT NULL OR p.team IS NOT NULL)
            """)
    List<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanIdWithResults();

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(long exerciseId, String username);

    List<ProgrammingExerciseStudentParticipation> findAllByExerciseIdAndStudentLogin(long exerciseId, String username);

    default ProgrammingExerciseStudentParticipation findByExerciseIdAndStudentLoginOrThrow(long exerciseId, String username) {
        return getValueElseThrow(findByExerciseIdAndStudentLogin(exerciseId, username));
    }

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndStudentLogin(long exerciseId, String username);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByExerciseIdAndStudentLoginOrThrow(long exerciseId, String username) {
        return getValueElseThrow(findWithSubmissionsByExerciseIdAndStudentLogin(exerciseId, username));
    }

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLoginAndTestRun(long exerciseId, String username, boolean testRun);

    @EntityGraph(type = LOAD, attributePaths = { "team.students" })
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(long exerciseId, long teamId);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students
            WHERE participation.exercise.id = :exerciseId
                AND participation.team.shortName = :teamShortName
            """)
    Optional<ProgrammingExerciseStudentParticipation> findWithEagerStudentsByExerciseIdAndTeamShortName(@Param("exerciseId") long exerciseId,
            @Param("teamShortName") String teamShortName);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students
            WHERE participation.exercise.id = :exerciseId
                AND participation.team.shortName = :teamShortName
            """)
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsAndEagerStudentsByExerciseIdAndTeamShortName(@Param("exerciseId") long exerciseId,
            @Param("teamShortName") String teamShortName);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students student
            WHERE participation.exercise.id = :exerciseId
                AND student.id = :studentId
            """)
    Optional<ProgrammingExerciseStudentParticipation> findTeamParticipationByExerciseIdAndStudentId(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    List<ProgrammingExerciseStudentParticipation> findByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "team.students" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsById(long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "team.students" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsAndTeamStudentsByExerciseId(long exerciseId);

    /**
     * Will return the participations matching the provided participation ids, but only if they belong to the given exercise.
     *
     * @param exerciseId       is used as a filter for the found participations.
     * @param participationIds the participations to retrieve.
     * @return filtered list of participations.
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

    @Query("""
            SELECT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
            WHERE participation.exercise.id = :exerciseId
                AND participation.student.login = :username
                AND participation.testRun = :testRun
            """)
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndStudentLoginAndTestRun(@Param("exerciseId") long exerciseId,
            @Param("username") String username, @Param("testRun") boolean testRun);

    @Query("""
            SELECT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
            WHERE participation.exercise.id = :exerciseId
                AND participation.student.login = :username
            ORDER BY participation.testRun ASC
            """)
    List<ProgrammingExerciseStudentParticipation> findAllWithSubmissionsByExerciseIdAndStudentLogin(@Param("exerciseId") long exerciseId, @Param("username") String username);

    @EntityGraph(type = LOAD, attributePaths = "team.students")
    Optional<ProgrammingExerciseStudentParticipation> findWithTeamStudentsById(long participationId);

    default Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(long participationId) {
        return findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }

    default Optional<ProgrammingExerciseStudentParticipation> findByIdWithAllResultsAndRelatedSubmissions(long participationId) {
        return findByIdWithAllResultsAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }

    default ProgrammingExerciseStudentParticipation findWithTeamStudentsByIdElseThrow(long participationId) {
        return getValueElseThrow(findWithTeamStudentsById(participationId), participationId);
    }

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseStudentParticipation p
            SET p.locked = :locked
            WHERE p.id = :participationId
            """)
    void updateLockedById(@Param("participationId") long participationId, @Param("locked") boolean locked);

    /**
     * Remove the build plan id from all participations of the given exercise.
     * This is used when the build plan is changed for an exercise, and we want to remove the old build plan id from all participations.
     * By deleting the build plan in the CI platform and unsetting the build plan id in the participations, the build plan is effectively removed
     * and will be regenerated/recreated on the next submission.
     *
     * @param exerciseId the id of the exercise for which the build plan id should be removed
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseStudentParticipation p
            SET p.buildPlanId = NULL, p.initializationState = de.tum.cit.aet.artemis.domain.enumeration.InitializationState.INACTIVE
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.initializationState = de.tum.cit.aet.artemis.domain.enumeration.InitializationState.INITIALIZED
            """)
    void unsetBuildPlanIdForExercise(@Param("exerciseId") Long exerciseId);
}
