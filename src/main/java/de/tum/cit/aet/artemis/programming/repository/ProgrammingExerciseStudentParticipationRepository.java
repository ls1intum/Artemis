package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.RepositoryUriConversionUtil;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ProgrammingExerciseStudentParticipationRepository extends ArtemisJpaRepository<ProgrammingExerciseStudentParticipation, Long> {

    /**
     * Loads a {@link ProgrammingExerciseStudentParticipation} by id with all related submissions and results in one query (avoiding N+1 issues via {@code LEFT JOIN FETCH}).
     *
     * <p>
     * Includes results if:
     * <ul>
     * <li>they are automatic,</li>
     * <li>they are completed and the assessment due date is before {@code dateTime} (or not set), or</li>
     * <li>no result exists yet.</li>
     * </ul>
     *
     * <p>
     * This ensures automatic feedback is always visible, manual assessments are only shown
     * after due dates, and participations without results remain accessible.
     * </p>
     *
     * @param participationId the participation id
     * @param dateTime        reference time for assessment due date checks
     * @return the participation with submissions and relevant results, if found
     */
    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.id = :participationId
                AND (
                    r.assessmentType = 'AUTOMATIC'
                    OR (r.completionDate IS NOT NULL AND (p.exercise.assessmentDueDate IS NULL OR p.exercise.assessmentDueDate < :dateTime))
                    OR r.id IS NULL
                    )
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithAllResultsAndRelatedSubmissions(@Param("participationId") long participationId,
            @Param("dateTime") ZonedDateTime dateTime);

    @EntityGraph(type = LOAD, attributePaths = { "submissions.results", "exercise", "team.students" })
    List<ProgrammingExerciseStudentParticipation> findWithResultsAndExerciseAndTeamStudentsByBuildPlanId(String buildPlanId);

    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.buildPlanId IS NOT NULL
                AND (p.student IS NOT NULL OR p.team IS NOT NULL)
            """)
    List<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanIdWithResults();

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(long exerciseId, String username);

    List<ProgrammingExerciseStudentParticipation> findAllByExerciseIdAndStudentLogin(long exerciseId, String username);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByRepositoryUri(String repositoryUri);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByRepositoryUriElseThrow(String repositoryUri) {
        String shortRepositoryUri = RepositoryUriConversionUtil.toShortRepositoryUri(repositoryUri);
        return getValueElseThrow(findWithSubmissionsByRepositoryUri(shortRepositoryUri));
    }

    Optional<ProgrammingExerciseStudentParticipation> findByRepositoryUri(String repositoryUri);

    default ProgrammingExerciseStudentParticipation findByRepositoryUriElseThrow(String repositoryUri) {
        String shortRepositoryUri = RepositoryUriConversionUtil.toShortRepositoryUri(repositoryUri);
        return getValueElseThrow(findByRepositoryUri(shortRepositoryUri));
    }

    @EntityGraph(type = LOAD, attributePaths = { "team.students" })
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(long exerciseId, long teamId);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students student
            WHERE participation.exercise.id = :exerciseId
                AND student.id = :studentId
            """)
    Optional<ProgrammingExerciseStudentParticipation> findTeamParticipationByExerciseIdAndStudentId(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions.results" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsAndResultsByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "team.students" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsAndTeamStudentsByExerciseId(long exerciseId);

    @Query("""
            SELECT DISTINCT participation
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN FETCH participation.submissions s
            WHERE participation.exercise.id = :exerciseId
                AND s.id = (SELECT MAX(s2.id)
                            FROM participation.submissions s2)
            """)
    Set<ProgrammingExerciseStudentParticipation> findWithLatestSubmissionByExerciseId(@Param("exerciseId") long exerciseId);

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
            SELECT participation.repositoryUri
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN TREAT (participation.exercise AS ProgrammingExercise) pe
                LEFT JOIN pe.exerciseGroup eg
                LEFT JOIN eg.exam exam
            WHERE participation.repositoryUri IS NOT NULL
                AND (
                    (pe.dueDate IS NOT NULL AND pe.dueDate BETWEEN :earliestDate AND :latestDate)
                    OR (eg IS NOT NULL AND exam IS NOT NULL AND exam.endDate BETWEEN :earliestDate AND :latestDate)
                )
            """)
    Page<String> findRepositoryUrisByRecentDueDateOrRecentExamEndDate(@Param("earliestDate") ZonedDateTime earliestDate, @Param("latestDate") ZonedDateTime latestDate,
            Pageable pageable);

    @Query("""
            SELECT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.submissions
            WHERE participation.exercise.id = :exerciseId
                AND participation.student.login = :username
            ORDER BY participation.testRun ASC
            """)
    List<ProgrammingExerciseStudentParticipation> findAllWithSubmissionsByExerciseIdAndStudentLogin(@Param("exerciseId") long exerciseId, @Param("username") String username);

    @Query("""
            SELECT participation
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students student
                LEFT JOIN FETCH participation.submissions
            WHERE participation.exercise.id = :exerciseId
                AND student.login = :username
            ORDER BY participation.testRun ASC
            """)
    List<ProgrammingExerciseStudentParticipation> findAllWithSubmissionByExerciseIdAndStudentLoginInTeam(@Param("exerciseId") long exerciseId, @Param("username") String username);

    @EntityGraph(type = LOAD, attributePaths = "team.students")
    Optional<ProgrammingExerciseStudentParticipation> findWithTeamStudentsById(long participationId);

    default Optional<ProgrammingExerciseStudentParticipation> findByIdWithAllResultsAndRelatedSubmissions(long participationId) {
        return findByIdWithAllResultsAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }

    default ProgrammingExerciseStudentParticipation findWithTeamStudentsByIdElseThrow(long participationId) {
        return getValueElseThrow(findWithTeamStudentsById(participationId), participationId);
    }

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
            SET p.buildPlanId = NULL, p.initializationState = de.tum.cit.aet.artemis.exercise.domain.InitializationState.INACTIVE
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.initializationState = de.tum.cit.aet.artemis.exercise.domain.InitializationState.INITIALIZED
            """)
    void unsetBuildPlanIdForExercise(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT p.id
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :exerciseId
                AND p.student.login IN :participantIdentifierList
            """)
    Set<Long> findIdsByExerciseIdAndParticipantIdentifier(@Param("exerciseId") long exerciseId, @Param("participantIdentifierList") Set<String> participantIdentifierList);

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :exerciseId
            """)
    Set<ProgrammingExerciseStudentParticipation> findByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :exerciseId
            """)
    Set<ProgrammingExerciseStudentParticipation> findByExerciseIdWithEagerSubmissions(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.id IN :participationIds
            """)
    Set<ProgrammingExerciseStudentParticipation> findByIds(@Param("participationIds") Collection<Long> participationIds);

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.id IN :participationIds
            """)
    Set<ProgrammingExerciseStudentParticipation> findByIdsWithEagerSubmissions(@Param("participationIds") Collection<Long> participationIds);
}
