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
import de.tum.cit.aet.artemis.programming.dto.ParticipationBuildTriggerDTO;

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
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsById(long participationId);

    /**
     * Resolves a student's non-test-run participation in an exercise by the student's id rather than their login. Used by
     * {@code MilestoneScoreService}, which is driven by result events carrying a participant id (see
     * {@code ResultListener}) and would otherwise have to load the user just to read their login back out.
     * <p>
     * Milestone groups are individual-participation only (team milestones are not part of the repository-sharing scheme,
     * see {@code ParticipationService}), so a student has at most one such participation per exercise.
     *
     * @param exerciseId the id of the exercise
     * @param studentId  the id of the student
     * @return the participation, or empty if the student has not started the exercise
     */
    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :exerciseId
                AND p.student.id = :studentId
                AND p.testRun = FALSE
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentId(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    /**
     * All non-test-run participations of the given exercise that already have a real repository - used to find every
     * student who already shares a {@code MilestoneExerciseGroup}'s repository (via its {@code MilestoneExercise}'s
     * participations), so a newly created {@code UserStoryExercise} can backfill a participation for each of them (see
     * {@code ParticipationService.provisionParticipationsForNewUserStoryExercise}).
     *
     * @param exerciseId the id of the exercise (typically the group's {@code MilestoneExercise})
     * @return the matching participations
     */
    List<ProgrammingExerciseStudentParticipation> findAllByExerciseIdAndRepositoryUriIsNotNullAndTestRunFalse(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByRepositoryUri(String repositoryUri);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByRepositoryUriElseThrow(String repositoryUri) {
        return getValueElseThrow(findWithSubmissionsByRepositoryUri(repositoryUri));
    }

    /**
     * Like {@link #findWithSubmissionsByRepositoryUri}, but also scoped to a specific exercise - needed for the exact
     * same reason as {@link #findByExerciseIdAndRepositoryUri}: a {@code UserStoryExercise} participation can share its
     * {@code MilestoneExerciseGroup}'s repository, so multiple participations of different exercises can legitimately
     * have the exact same {@code repositoryUri}. Used by the online code editor's "commit" push-processing path
     * ({@code ProgrammingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository}), which - unlike a
     * real git push - already knows which participation (and therefore which exercise) the user committed through.
     *
     * @param exerciseId    the id of the exercise the participation belongs to
     * @param repositoryUri the repository URI to look up
     * @return the participation, or empty if none exists for that exercise/repository pair
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndRepositoryUri(long exerciseId, String repositoryUri);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByExerciseIdAndRepositoryUriElseThrow(long exerciseId, String repositoryUri) {
        return getValueElseThrow(findWithSubmissionsByExerciseIdAndRepositoryUri(exerciseId, repositoryUri));
    }

    // exercise and student are eager @ManyToOne associations, so without fetching them here Hibernate issues a secondary
    // select for each. Git authorization resolves a participation by repository uri on every git request and then reads
    // both, so those two selects would repeat on every fetch and every push.
    @EntityGraph(type = LOAD, attributePaths = { "exercise", "exercise.course", "student" })
    Optional<ProgrammingExerciseStudentParticipation> findByRepositoryUri(String repositoryUri);

    default ProgrammingExerciseStudentParticipation findByRepositoryUriElseThrow(String repositoryUri) {
        return getValueElseThrow(findByRepositoryUri(repositoryUri));
    }

    /**
     * Like {@link #findByRepositoryUri}, but also scoped to a specific exercise - needed because a {@code UserStoryExercise}
     * participation can share its {@code MilestoneExerciseGroup}'s repository (see {@code ParticipationService.startUserStoryExercise}
     * / {@code provisionUserStoryParticipationsForGroup}), so multiple participations of different exercises can
     * legitimately have the exact same {@code repositoryUri}; {@link #findByRepositoryUri} alone would then throw
     * {@code NonUniqueResultException}. Git fetch/push access to such a repository always resolves the exercise from the
     * repository's project key first (which is always the milestone's, since siblings physically live under it), so
     * scoping by that already-known exercise id disambiguates cleanly.
     *
     * @param exerciseId    the id of the exercise the participation belongs to
     * @param repositoryUri the repository URI to look up
     * @return the participation, or empty if none exists for that exercise/repository pair
     */
    // Carries the same entity graph as findByRepositoryUri, and for the same reason: this is the git authorization
    // path, which reads exercise, course and student right after resolving the participation.
    @EntityGraph(type = LOAD, attributePaths = { "exercise", "exercise.course", "student" })
    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndRepositoryUri(long exerciseId, String repositoryUri);

    default ProgrammingExerciseStudentParticipation findByExerciseIdAndRepositoryUriElseThrow(long exerciseId, String repositoryUri) {
        return getValueElseThrow(findByExerciseIdAndRepositoryUri(exerciseId, repositoryUri));
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

    /**
     * Returns the ids of all student participations of the given programming exercise. Used to build a per-participation
     * result without loading the participations (and their submissions/results) themselves.
     *
     * @param exerciseId the id of the programming exercise
     * @return the ids of all student participations of the exercise
     */
    @Query("""
            SELECT p.id
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :exerciseId
            """)
    List<Long> findStudentParticipationIdsByExerciseId(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "team.students" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsAndTeamStudentsByExerciseId(long exerciseId);

    /**
     * Returns what a build trigger reads off every participation of the exercise that has something to build.
     * <p>
     * Only the newest submission of each participation is considered, and a participation without any submission is
     * left out entirely because triggering it is a no-op.
     * <p>
     * The projection is deliberate. Loading the participations as entities makes Hibernate resolve their eager student
     * association with one query per participation, so an exercise with a thousand participations costs a thousand and
     * two queries and ships a full user row, password hash included, for each of them. This is one query and holds only
     * the columns the trigger looks at.
     *
     * @param exerciseId the exercise whose participations should be triggered
     * @return the trigger inputs of every participation of the exercise that has a submission
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ParticipationBuildTriggerDTO(
                participation.id, participation.repositoryUri, participation.buildPlanId, participation.branch,
                participation.initializationState, participation.individualDueDate, participation.testRun,
                student.id, student.login, team.id,
                submission.id, submission.type, submission.submissionDate, submission.commitHash,
                submission.submitted, submission.buildFailed, submission.exampleSubmission)
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN participation.student student
                LEFT JOIN participation.team team
                JOIN TREAT (participation.submissions AS ProgrammingSubmission) submission
            WHERE participation.exercise.id = :exerciseId
                AND submission.id = (SELECT MAX(s2.id)
                                     FROM participation.submissions s2)
            """)
    List<ParticipationBuildTriggerDTO> findBuildTriggerDataByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Returns what a build trigger reads off the given participations, but only for those that belong to the given
     * exercise and have something to build.
     * <p>
     * Only the newest submission of each participation is considered. The caller triggers a build for that one, so
     * fetching every submission a student ever pushed in order to read the last one is a lot of rows for nothing: on
     * the "trigger all failed builds" path that would be every push of every selected participation. Participations
     * without any submission are left out because triggering them is a no-op.
     * <p>
     * Newest means the highest id, which is how the exercise-wide query above has always picked it. This path used to
     * load every submission and pick the newest in memory, where {@link de.tum.cit.aet.artemis.exercise.domain.Submission}
     * orders by submission date and falls back to the id. The two only disagree when a submission carries a date that
     * is older than that of a submission inserted before it, which is why the id is the more direct answer to "the
     * commit that was pushed last".
     *
     * @param exerciseId       is used as a filter for the found participations
     * @param participationIds the participations to retrieve
     * @return the trigger inputs of the requested participations that belong to the exercise and have a submission
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ParticipationBuildTriggerDTO(
                participation.id, participation.repositoryUri, participation.buildPlanId, participation.branch,
                participation.initializationState, participation.individualDueDate, participation.testRun,
                student.id, student.login, team.id,
                submission.id, submission.type, submission.submissionDate, submission.commitHash,
                submission.submitted, submission.buildFailed, submission.exampleSubmission)
            FROM ProgrammingExerciseStudentParticipation participation
                LEFT JOIN participation.student student
                LEFT JOIN participation.team team
                JOIN TREAT (participation.submissions AS ProgrammingSubmission) submission
            WHERE participation.exercise.id = :exerciseId
                AND participation.id IN :participationIds
                AND submission.id = (SELECT MAX(s2.id)
                                     FROM participation.submissions s2)
            """)
    List<ParticipationBuildTriggerDTO> findBuildTriggerDataByExerciseIdAndParticipationIds(@Param("exerciseId") long exerciseId,
            @Param("participationIds") Collection<Long> participationIds);

    @Query("""
            SELECT pa.repositoryUri
            FROM ProgrammingExercise pe
                LEFT JOIN TREAT (pe.studentParticipations AS ProgrammingExerciseStudentParticipation) pa
            WHERE pa.repositoryUri IS NOT NULL
                AND pe.dueDate BETWEEN :earliestDate AND :latestDate
            """)
    Page<String> findRepositoryUrisByCourseExerciseDueDateBetween(@Param("earliestDate") ZonedDateTime earliestDate, @Param("latestDate") ZonedDateTime latestDate,
            Pageable pageable);

    @Query("""
            SELECT pa.repositoryUri
            FROM ProgrammingExercise pe
                LEFT JOIN pe.exerciseGroup eg
                LEFT JOIN eg.exam exam
                LEFT JOIN TREAT (pe.studentParticipations AS ProgrammingExerciseStudentParticipation) pa
            WHERE pa.repositoryUri IS NOT NULL
                AND exam.endDate BETWEEN :earliestDate AND :latestDate
            """)
    Page<String> findRepositoryUrisByExamExercisesEndDateBetween(@Param("earliestDate") ZonedDateTime earliestDate, @Param("latestDate") ZonedDateTime latestDate,
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

    boolean existsByExerciseId(long exerciseId);

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
