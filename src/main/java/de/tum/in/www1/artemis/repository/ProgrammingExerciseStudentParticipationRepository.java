package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

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
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseStudentParticipationRepository extends JpaRepository<ProgrammingExerciseStudentParticipation, Long> {

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.results pr
                LEFT JOIN FETCH pr.feedbacks
                LEFT JOIN FETCH pr.submission
            WHERE p.id = :participationId
                AND (pr.id = (
                    SELECT max(prr.id)
                    FROM p.results prr
                    WHERE (prr.assessmentType = 'AUTOMATIC'
                        OR (prr.completionDate IS NOT NULL
                            AND (p.exercise.assessmentDueDate IS NULL
                                OR p.exercise.assessmentDueDate < :#{#dateTime}))))
                    OR pr.id IS NULL)
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(@Param("participationId") Long participationId,
            @Param("dateTime") ZonedDateTime dateTime);

    /**
     * Will return the participation with the provided participationId. The participation will come with all its manual results, submissions, feedbacks and assessors
     *
     * @param participationId the participation id
     * @return a participation with all its manual results.
     */
    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.results pr
                LEFT JOIN FETCH pr.feedbacks
                LEFT JOIN FETCH pr.submission
                LEFT JOIN FETCH pr.assessor
            WHERE p.id = :participationId
                AND pr.id IN (
                    SELECT prr.id
                    FROM p.results prr
                    WHERE prr.assessmentType = 'MANUAL'
                        OR prr.assessmentType = 'SEMI_AUTOMATIC')
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithAllManualOrSemiAutomaticResultsAndFeedbacksAndRelatedSubmissionAndAssessor(
            @Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "exercise" })
    List<ProgrammingExerciseStudentParticipation> findByBuildPlanId(String buildPlanId);

    @Query("""
                SELECT DISTINCT p
                FROM ProgrammingExerciseStudentParticipation p
                    LEFT JOIN FETCH p.results
                    WHERE p.buildPlanId IS NOT NULL
                        AND (p.student IS NOT NULL
                            OR p.team IS NOT NULL)
            """)
    List<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanIdWithResults();

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    default ProgrammingExerciseStudentParticipation findByExerciseIdAndStudentLoginOrThrow(Long exerciseId, String username) {
        return findByExerciseIdAndStudentLogin(exerciseId, username).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndStudentLogin(Long exerciseId, String username);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByExerciseIdAndStudentLoginOrThrow(Long exerciseId, String username) {
        return findWithSubmissionsByExerciseIdAndStudentLogin(exerciseId, username)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", exerciseId));
    }

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLoginAndTestRun(Long exerciseId, String username, boolean testRun);

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students
            WHERE participation.exercise.id = :#{#exerciseId}
                AND participation.team.shortName = :#{#teamShortName}
            """)
    Optional<ProgrammingExerciseStudentParticipation> findWithEagerStudentsByExerciseIdAndTeamShortName(@Param("exerciseId") Long exerciseId,
            @Param("teamShortName") String teamShortName);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students
            WHERE participation.exercise.id = :#{#exerciseId}
                AND participation.team.shortName = :#{#teamShortName}
            """)
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsAndEagerStudentsByExerciseIdAndTeamShortName(@Param("exerciseId") Long exerciseId,
            @Param("teamShortName") String teamShortName);

    List<ProgrammingExerciseStudentParticipation> findByExerciseId(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseId(Long exerciseId);

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
            WHERE participation.exercise.id = :#{#exerciseId}
                AND participation.id IN :#{#participationIds}
            """)
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndParticipationIds(@Param("exerciseId") Long exerciseId,
            @Param("participationIds") Collection<Long> participationIds);

    @Query("""
            SELECT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
            WHERE participation.exercise.id = :#{#exerciseId}
                AND participation.student.login = :#{#username}
                AND participation.testRun = :#{#testRun}
            """)
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndStudentLoginAndTestRun(@Param("exerciseId") Long exerciseId,
            @Param("username") String username, @Param("testRun") boolean testRun);

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.individualDueDate IS NOT NULL
            """)
    List<ProgrammingExerciseStudentParticipation> findWithIndividualDueDateByExerciseId(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    ProgrammingExerciseStudentParticipation findWithResultsById(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "student")
    Optional<ProgrammingExerciseStudentParticipation> findWithStudentById(Long participationId);

    @NotNull
    default ProgrammingExerciseStudentParticipation findByIdElseThrow(Long participationId) {
        return findById(participationId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", participationId));
    }

    default Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(Long participationId) {
        return findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseStudentParticipation p
            SET p.locked = :#{#locked}
            WHERE p.id = :#{#participationId}
            """)
    void updateLockedById(@Param("participationId") Long participationId, @Param("locked") boolean locked);
}
