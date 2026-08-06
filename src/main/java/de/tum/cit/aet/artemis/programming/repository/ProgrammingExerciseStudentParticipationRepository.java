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
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationProjectionDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

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

    @EntityGraph(type = LOAD, attributePaths = { "student", "exercise", "irisAssessment" })
    Optional<ProgrammingExerciseStudentParticipation> findWithIrisAssessmentById(long participationId);

    @EntityGraph(type = LOAD, attributePaths = "irisAssessment")
    Optional<ProgrammingExerciseStudentParticipation> findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(long exerciseId, String username, boolean testRun);

    @EntityGraph(type = LOAD, attributePaths = "irisAssessmentInClass")
    Optional<ProgrammingExerciseStudentParticipation> findWithIrisAssessmentInClassByExerciseIdAndStudentLoginAndTestRun(long exerciseId, String username, boolean testRun);

    /**
     * Loads the participation with the Iris assessment eagerly fetched, choosing between the regular and the in-class assessment
     * depending on the {@code inClass} flag.
     *
     * @param exerciseId the id of the exercise
     * @param username   the login of the student
     * @param inClass    whether the in-class Iris assessment should be loaded instead of the regular one
     * @param testRun    whether the participation is a test run participation
     * @return the participation with the corresponding Iris assessment eagerly fetched, if found
     */
    default Optional<ProgrammingExerciseStudentParticipation> findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(long exerciseId, String username, boolean inClass,
            boolean testRun) {
        return inClass ? findWithIrisAssessmentInClassByExerciseIdAndStudentLoginAndTestRun(exerciseId, username, testRun)
                : findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exerciseId, username, testRun);
    }

    List<ProgrammingExerciseStudentParticipation> findAllByExerciseIdAndStudentLogin(long exerciseId, String username);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ProgrammingExerciseStudentParticipation> findWithSubmissionsByRepositoryUri(String repositoryUri);

    default ProgrammingExerciseStudentParticipation findWithSubmissionsByRepositoryUriElseThrow(String repositoryUri) {
        return getValueElseThrow(findWithSubmissionsByRepositoryUri(repositoryUri));
    }

    Optional<ProgrammingExerciseStudentParticipation> findByRepositoryUri(String repositoryUri);

    default ProgrammingExerciseStudentParticipation findByRepositoryUriElseThrow(String repositoryUri) {
        return getValueElseThrow(findByRepositoryUri(repositoryUri));
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

    /**
     * Finds the ids of all in-class Iris assessments that belong to participations of the given exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the ids of the in-class Iris assessments linked to participations of the exercise
     */
    @Query("""
            SELECT assessment.id
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN participation.irisAssessmentInClass assessment
            WHERE participation.exercise.id = :exerciseId
            """)
    Set<Long> findIrisAssessmentInClassIdsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Removes the reference to the in-class Iris assessment from all participations of the given exercise, without deleting the assessments themselves.
     * Used to detach in-class assessments from their participations, e.g. before a new in-class quiz run.
     *
     * @param exerciseId the id of the exercise for which the in-class Iris assessment references should be unset
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExerciseStudentParticipation participation
            SET participation.irisAssessmentInClass = NULL
            WHERE participation.exercise.id = :exerciseId
                AND participation.irisAssessmentInClass IS NOT NULL
            """)
    void unsetIrisAssessmentInClassByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Finds Iris assessment participation projections for participations whose latest result has a positive score.
     *
     * @param exerciseId the exercise id
     * @return matching participation projections
     */
    default Set<IrisAssessmentProgrammingStudentParticipationProjectionDTO> findAllNonPracticeIrisAssessmentParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(
            long exerciseId) {
        var participationIds = findParticipationIdsWithLatestResultScoreGreaterThanZeroAndNotPractice(exerciseId);
        if (participationIds.isEmpty()) {
            return Set.of();
        }
        return findAllIrisAssessmentParticipationProjectionsByIdIn(participationIds);
    }

    /**
     * Finds in-class Iris assessment participation projections for participations whose latest result has a positive score.
     *
     * @param exerciseId the exercise id
     * @return matching participation projections
     */
    default Set<IrisAssessmentProgrammingStudentParticipationProjectionDTO> findAllNonPracticeIrisAssessmentInClassParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(
            long exerciseId) {
        var participationIds = findParticipationIdsWithLatestResultScoreGreaterThanZeroAndNotPractice(exerciseId);
        if (participationIds.isEmpty()) {
            return Set.of();
        }
        return findAllIrisAssessmentInClassParticipationProjectionsByIdIn(participationIds);
    }

    /**
     * Resolves the ids of non-practice participations of the given exercise whose latest submission's latest result has a positive score.
     * Chains three queries (latest submission ids, latest result ids, participation ids), short-circuiting with an empty set as soon as one step yields no results.
     *
     * @param exerciseId the exercise id
     * @return the ids of the matching participations
     */
    private Set<Long> findParticipationIdsWithLatestResultScoreGreaterThanZeroAndNotPractice(long exerciseId) {
        var latestSubmissionIds = findLatestSubmissionIdsByExerciseId(exerciseId);
        if (latestSubmissionIds.isEmpty()) {
            return Set.of();
        }

        var latestResultIds = findLatestResultIdsBySubmissionIds(latestSubmissionIds);
        if (latestResultIds.isEmpty()) {
            return Set.of();
        }

        return findParticipationIdsByResultIdsAndScoreGreaterThanZeroAndNotPractice(latestResultIds);
    }

    /**
     * Finds the ids of the latest submission per non-team participation of the given exercise, restricted to submissions whose latest result has a positive score
     * and for which no strictly newer submission (by submission date, falling back to id) with a positive-score latest result exists.
     *
     * @param exerciseId the exercise id
     * @return the ids of the qualifying latest submissions
     */
    @Query("""
            SELECT submission.id
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN participation.submissions submission
                JOIN submission.results latestResult
            WHERE participation.exercise.id = :exerciseId
                AND participation.student IS NOT NULL
                AND latestResult.id = (
                    SELECT MAX(result.id)
                    FROM Result result
                    WHERE result.submission.id = submission.id
                )
                AND latestResult.score > 0
                AND NOT EXISTS (
                    SELECT newerSubmission.id
                    FROM ProgrammingSubmission newerSubmission
                        JOIN newerSubmission.results newerLatestResult
                    WHERE newerSubmission.participation.id = participation.id
                        AND ((submission.submissionDate IS NOT NULL AND newerSubmission.submissionDate IS NOT NULL AND newerSubmission.submissionDate > submission.submissionDate)
                            OR ((submission.submissionDate IS NULL OR newerSubmission.submissionDate IS NULL OR newerSubmission.submissionDate = submission.submissionDate)
                                AND newerSubmission.id > submission.id))
                        AND newerLatestResult.id = (
                            SELECT MAX(newerResult.id)
                            FROM Result newerResult
                            WHERE newerResult.submission.id = newerSubmission.id
                        )
                        AND newerLatestResult.score > 0
                )
            """)
    Set<Long> findLatestSubmissionIdsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Finds, for each of the given submissions, the id of its latest result.
     *
     * @param submissionIds the ids of the submissions
     * @return the ids of the latest result per submission
     */
    @Query("""
            SELECT MAX(result.id)
            FROM Result result
            WHERE result.submission.id IN :submissionIds
            GROUP BY result.submission.id
            """)
    Set<Long> findLatestResultIdsBySubmissionIds(@Param("submissionIds") Set<Long> submissionIds);

    /**
     * Finds the ids of the (non-practice) participations whose result, among the given result ids, has a positive score.
     *
     * @param resultIds the ids of the results to filter on
     * @return the ids of the matching non-practice participations
     */
    @Query("""
            SELECT result.submission.participation.id
            FROM Result result
            WHERE result.id IN :resultIds
                AND result.score > 0
                AND result.submission.participation.testRun = false
            """)
    Set<Long> findParticipationIdsByResultIdsAndScoreGreaterThanZeroAndNotPractice(@Param("resultIds") Set<Long> resultIds);

    /**
     * Loads Iris assessment participation projections for the given participation ids, joined with their (optional) regular Iris assessment.
     *
     * @param participationIds the ids of the participations to project
     * @return the projections for the matching participations
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationProjectionDTO(
                participation.id,
                participation.exercise.id,
                participation.repositoryUri,
                participation.buildPlanId,
                student.login,
                student.firstName,
                student.lastName,
                assessment.id,
                assessment.verdict,
                assessment.verdictReview
            )
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN participation.student student
                LEFT JOIN participation.irisAssessment assessment
            WHERE participation.id IN :participationIds
            """)
    Set<IrisAssessmentProgrammingStudentParticipationProjectionDTO> findAllIrisAssessmentParticipationProjectionsByIdIn(@Param("participationIds") Set<Long> participationIds);

    /**
     * Loads Iris assessment participation projections for the given participation ids, joined with their (optional) in-class Iris assessment.
     *
     * @param participationIds the ids of the participations to project
     * @return the projections for the matching participations
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationProjectionDTO(
                participation.id,
                participation.exercise.id,
                participation.repositoryUri,
                participation.buildPlanId,
                student.login,
                student.firstName,
                student.lastName,
                assessment.id,
                assessment.verdict,
                assessment.verdictReview
            )
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN participation.student student
                LEFT JOIN participation.irisAssessmentInClass assessment
            WHERE participation.id IN :participationIds
            """)
    Set<IrisAssessmentProgrammingStudentParticipationProjectionDTO> findAllIrisAssessmentInClassParticipationProjectionsByIdIn(
            @Param("participationIds") Set<Long> participationIds);

    /**
     * Searches, paginates and filters non-practice participation ids of a course's programming exercises for the Iris assessment review overview.
     * <p>
     * A participation is only included if its latest submission's latest result has a positive score (see the {@code EXISTS} subquery, mirroring the logic of
     * {@link #findLatestSubmissionIdsByExerciseId(long)}). The result is further restricted by an optional student name/login search pattern and, if any verdict
     * filter is selected, by the Iris verdict/verdict-review of either the regular or the in-class Iris assessment, depending on {@code inClass}.
     *
     * @param courseId             the id of the course whose participations are searched
     * @param searchPattern        a lower-cased {@code LIKE} pattern matched against the student's login or full name, or {@code null} to disable the name filter
     * @param inClass              whether the in-class Iris assessment (instead of the regular one) should be used for the verdict filters
     * @param hasSelectedFilter    whether at least one of the verdict filters below is active; if {@code false}, no verdict filtering is applied
     * @param acceptedSelected     whether to include participations whose relevant assessment verdict review is {@code ACCEPTED}
     * @param rejectedSelected     whether to include participations whose relevant assessment verdict review is {@code REJECTED}
     * @param unsuspiciousSelected whether to include participations whose relevant assessment verdict is {@code UNSUSPICIOUS} and not yet reviewed
     * @param suspiciousSelected   whether to include participations whose relevant assessment verdict is {@code SUSPICIOUS} and not yet reviewed
     * @param missingSelected      whether to include participations that have no relevant assessment (or no verdict) yet
     * @param pageable             the pagination and sorting information
     * @return a page of matching participation ids, ordered by exercise title and student name
     */
    @Query(value = """
            SELECT participation.id
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN participation.student student
                LEFT JOIN participation.irisAssessment assessment
                LEFT JOIN participation.irisAssessmentInClass inClassAssessment
            WHERE participation.exercise.course.id = :courseId
                AND (participation.testRun IS NULL OR participation.testRun = false)
                AND (:searchPattern IS NULL
                    OR LOWER(student.login) LIKE :searchPattern ESCAPE '\\'
                    OR LOWER(CONCAT(CONCAT(COALESCE(student.firstName, ''), ' '), COALESCE(student.lastName, ''))) LIKE :searchPattern ESCAPE '\\')
                AND EXISTS (
                    SELECT submission.id
                    FROM ProgrammingSubmission submission
                        JOIN submission.results latestResult
                    WHERE submission.participation.id = participation.id
                        AND latestResult.id = (
                            SELECT MAX(result.id)
                            FROM Result result
                            WHERE result.submission.id = submission.id
                        )
                        AND latestResult.score > 0
                        AND NOT EXISTS (
                            SELECT newerSubmission.id
                            FROM ProgrammingSubmission newerSubmission
                                JOIN newerSubmission.results newerLatestResult
                            WHERE newerSubmission.participation.id = participation.id
                                AND ((submission.submissionDate IS NOT NULL AND newerSubmission.submissionDate IS NOT NULL AND newerSubmission.submissionDate > submission.submissionDate)
                                    OR ((submission.submissionDate IS NULL OR newerSubmission.submissionDate IS NULL OR newerSubmission.submissionDate = submission.submissionDate)
                                        AND newerSubmission.id > submission.id))
                                AND newerLatestResult.id = (
                                    SELECT MAX(newerResult.id)
                                    FROM Result newerResult
                                    WHERE newerResult.submission.id = newerSubmission.id
                                )
                                AND newerLatestResult.score > 0
                        )
                )
                AND (:hasSelectedFilter = false
                    OR (:acceptedSelected = true
                        AND ((:inClass = false AND assessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.ACCEPTED)
                            OR (:inClass = true AND inClassAssessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.ACCEPTED)))
                    OR (:rejectedSelected = true
                        AND ((:inClass = false AND assessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.REJECTED)
                            OR (:inClass = true AND inClassAssessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.REJECTED)))
                    OR (:unsuspiciousSelected = true
                        AND ((:inClass = false AND assessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.UNSUSPICIOUS AND assessment.verdictReview IS NULL)
                            OR (:inClass = true AND inClassAssessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.UNSUSPICIOUS AND inClassAssessment.verdictReview IS NULL)))
                    OR (:suspiciousSelected = true
                        AND ((:inClass = false AND assessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.SUSPICIOUS AND assessment.verdictReview IS NULL)
                            OR (:inClass = true AND inClassAssessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.SUSPICIOUS AND inClassAssessment.verdictReview IS NULL)))
                    OR (:missingSelected = true
                        AND ((:inClass = false AND (assessment.id IS NULL OR assessment.verdict IS NULL))
                            OR (:inClass = true AND (inClassAssessment.id IS NULL OR inClassAssessment.verdict IS NULL)))))
            ORDER BY participation.exercise.title ASC, student.lastName ASC, student.firstName ASC, participation.id ASC
            """, countQuery = """
            SELECT COUNT(participation.id)
            FROM ProgrammingExerciseStudentParticipation participation
                JOIN participation.student student
                LEFT JOIN participation.irisAssessment assessment
                LEFT JOIN participation.irisAssessmentInClass inClassAssessment
            WHERE participation.exercise.course.id = :courseId
                AND (participation.testRun IS NULL OR participation.testRun = false)
                AND (:searchPattern IS NULL
                    OR LOWER(student.login) LIKE :searchPattern ESCAPE '\\'
                    OR LOWER(CONCAT(CONCAT(COALESCE(student.firstName, ''), ' '), COALESCE(student.lastName, ''))) LIKE :searchPattern ESCAPE '\\')
                AND EXISTS (
                    SELECT submission.id
                    FROM ProgrammingSubmission submission
                        JOIN submission.results latestResult
                    WHERE submission.participation.id = participation.id
                        AND latestResult.id = (
                            SELECT MAX(result.id)
                            FROM Result result
                            WHERE result.submission.id = submission.id
                        )
                        AND latestResult.score > 0
                        AND NOT EXISTS (
                            SELECT newerSubmission.id
                            FROM ProgrammingSubmission newerSubmission
                                JOIN newerSubmission.results newerLatestResult
                            WHERE newerSubmission.participation.id = participation.id
                                AND ((submission.submissionDate IS NOT NULL AND newerSubmission.submissionDate IS NOT NULL AND newerSubmission.submissionDate > submission.submissionDate)
                                    OR ((submission.submissionDate IS NULL OR newerSubmission.submissionDate IS NULL OR newerSubmission.submissionDate = submission.submissionDate)
                                        AND newerSubmission.id > submission.id))
                                AND newerLatestResult.id = (
                                    SELECT MAX(newerResult.id)
                                    FROM Result newerResult
                                    WHERE newerResult.submission.id = newerSubmission.id
                                )
                                AND newerLatestResult.score > 0
                        )
                )
                AND (:hasSelectedFilter = false
                    OR (:acceptedSelected = true
                        AND ((:inClass = false AND assessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.ACCEPTED)
                            OR (:inClass = true AND inClassAssessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.ACCEPTED)))
                    OR (:rejectedSelected = true
                        AND ((:inClass = false AND assessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.REJECTED)
                            OR (:inClass = true AND inClassAssessment.verdictReview = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview.REJECTED)))
                    OR (:unsuspiciousSelected = true
                        AND ((:inClass = false AND assessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.UNSUSPICIOUS AND assessment.verdictReview IS NULL)
                            OR (:inClass = true AND inClassAssessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.UNSUSPICIOUS AND inClassAssessment.verdictReview IS NULL)))
                    OR (:suspiciousSelected = true
                        AND ((:inClass = false AND assessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.SUSPICIOUS AND assessment.verdictReview IS NULL)
                            OR (:inClass = true AND inClassAssessment.verdict = de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict.SUSPICIOUS AND inClassAssessment.verdictReview IS NULL)))
                    OR (:missingSelected = true
                        AND ((:inClass = false AND (assessment.id IS NULL OR assessment.verdict IS NULL))
                            OR (:inClass = true AND (inClassAssessment.id IS NULL OR inClassAssessment.verdict IS NULL)))))
            """)
    Page<Long> findIrisAssessmentReviewParticipationIds(@Param("courseId") long courseId, @Param("searchPattern") String searchPattern, @Param("inClass") boolean inClass,
            @Param("hasSelectedFilter") boolean hasSelectedFilter, @Param("acceptedSelected") boolean acceptedSelected, @Param("rejectedSelected") boolean rejectedSelected,
            @Param("unsuspiciousSelected") boolean unsuspiciousSelected, @Param("suspiciousSelected") boolean suspiciousSelected, @Param("missingSelected") boolean missingSelected,
            Pageable pageable);

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

    /**
     * Load participations by their IDs with the latest submission (individual mode).
     * Used as the data-loading step after the paginated ID query.
     *
     * @param ids the participation IDs to load
     * @return participations with student and latest submission eagerly fetched
     */
    @Query("""
            SELECT DISTINCT p
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN FETCH p.student
                LEFT JOIN FETCH p.irisAssessment
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.id IN :ids
                AND (s.id IS NULL
                    OR s.id = (
                        SELECT MAX(s2.id)
                        FROM Submission s2
                        WHERE s2.participation = p
                    ))
            """)
    List<ProgrammingExerciseStudentParticipation> findByIdsWithLatestSubmissionAndIrisAssessment(@Param("ids") Collection<Long> ids);
}
