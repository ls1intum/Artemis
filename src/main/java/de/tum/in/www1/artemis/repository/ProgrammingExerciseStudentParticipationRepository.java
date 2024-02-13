package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Repository
public interface ProgrammingExerciseStudentParticipationRepository extends JpaRepository<ProgrammingExerciseStudentParticipation, Long> {

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
                    WHERE (prr.assessmentType = de.tum.in.www1.artemis.domain.enumeration.AssessmentType.AUTOMATIC
                        OR (prr.completionDate IS NOT NULL
                            AND (p.exercise.assessmentDueDate IS NULL OR p.exercise.assessmentDueDate < :dateTime)
                        )
                    )
                ) OR pr.id IS NULL)
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(@Param("participationId") long participationId,
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

    default ProgrammingExerciseStudentParticipation findByExerciseIdAndStudentLoginOrThrow(long exerciseId, String username) {
        return findByExerciseIdAndStudentLogin(exerciseId, username).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndStudentLogin(long exerciseId, String username);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByExerciseIdAndStudentLoginOrThrow(long exerciseId, String username) {
        return findWithSubmissionsByExerciseIdAndStudentLogin(exerciseId, username)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", exerciseId));
    }

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLoginAndTestRun(long exerciseId, String username, boolean testRun);

    @EntityGraph(type = LOAD, attributePaths = { "team" })
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

    List<ProgrammingExerciseStudentParticipation> findByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
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

    @EntityGraph(type = LOAD, attributePaths = "student")
    Optional<ProgrammingExerciseStudentParticipation> findWithStudentById(long participationId);

    @EntityGraph(type = LOAD, attributePaths = "team.students")
    Optional<ProgrammingExerciseStudentParticipation> findWithTeamStudentsById(long participationId);

    @NotNull
    default ProgrammingExerciseStudentParticipation findByIdElseThrow(long participationId) {
        return findById(participationId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", participationId));
    }

    default Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(long participationId) {
        return findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }

    default ProgrammingExerciseStudentParticipation findWithTeamStudentsByIdElseThrow(long participationId) {
        return findWithTeamStudentsById(participationId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", participationId));
    }

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseStudentParticipation p
            SET p.locked = :locked
            WHERE p.id = :participationId
            """)
    void updateLockedById(@Param("participationId") long participationId, @Param("locked") boolean locked);

    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.buildPlanId IS NOT NULL
            """)
    Page<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanId(Pageable pageable);

    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.buildPlanId IS NOT NULL
                OR p.repositoryUri IS NOT NULL
            """)
    Page<ProgrammingExerciseStudentParticipation> findAllWithRepositoryUriOrBuildPlanId(Pageable pageable);
}
