package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

    /**
     * Does a max join on the result table for each participation by result id (the newer the result id, the newer the result).
     * This makes sure that we only receive the latest result for the template and the solution participation if they exist.
     *
     * @param courseId the course the returned programming exercises belong to.
     * @return all exercises for the given course with only the latest results for solution and template each (if present).
     */
    @Query("""
            SELECT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.templateParticipation tp
            LEFT JOIN FETCH pe.solutionParticipation sp
            LEFT JOIN FETCH tp.results tpr
            LEFT JOIN FETCH sp.results spr
            WHERE pe.course.id = :#{#courseId}
                AND (tpr.id = (SELECT MAX(id) FROM tp.results) OR tpr.id IS NULL)
                AND (spr.id = (SELECT MAX(id) FROM sp.results) OR spr.id IS NULL)
            """)
    List<ProgrammingExercise> findByCourseIdWithLatestResultForTemplateSolutionParticipations(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "templateParticipation.submissions", "solutionParticipation.submissions",
            "templateParticipation.submissions.results", "solutionParticipation.submissions.results" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationSubmissionsAndResultsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "testCases")
    Optional<ProgrammingExercise> findWithTestCasesById(Long exerciseId);

    /**
     * Get a programmingExercise with template and solution participation, each with the latest result and feedbacks.
     *
     * @param exerciseId the id of the exercise that should be fetched.
     * @return the exercise with the given ID, if found.
     */
    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.templateParticipation tp
            LEFT JOIN FETCH pe.solutionParticipation sp
            LEFT JOIN FETCH tp.results AS tpr
            LEFT JOIN FETCH sp.results AS spr
            LEFT JOIN FETCH tpr.feedbacks
            LEFT JOIN FETCH spr.feedbacks
            LEFT JOIN FETCH tpr.submission
            LEFT JOIN FETCH spr.submission
            WHERE pe.id = :#{#exerciseId}
                AND (tpr.id = (SELECT MAX(id) FROM tp.results) OR tpr.id IS NULL)
                AND (spr.id = (SELECT MAX(id) FROM sp.results) OR spr.id IS NULL)
            """)
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationLatestResultById(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.studentParticipations")
    List<ProgrammingExercise> findAllWithEagerParticipations();

    /**
     * Get all programming exercises that need to be scheduled: Those must satisfy one of the following requirements:
     * <ol>
     * <li>The release date is in the future --> Schedule combine template commits</li>
     * <li>The build and test student submissions after deadline date is in the future</li>
     * <li>Manual assessment is enabled and the due date is in the future</li>
     * </ol>
     *
     * @param now the current time
     * @return List of the exercises that should be scheduled
     */
    @Query("""
            select distinct pe from ProgrammingExercise pe
            where pe.releaseDate > :#{#now}
                or pe.buildAndTestStudentSubmissionsAfterDueDate > :#{#now}
                or (pe.assessmentType <> 'AUTOMATIC' and pe.dueDate > :#{#now})
            """)
    List<ProgrammingExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe WHERE pe.course.endDate BETWEEN :#{#endDate1} AND :#{#endDate2}")
    List<ProgrammingExercise> findAllByRecentCourseEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.studentParticipations
            WHERE pe.dueDate BETWEEN :#{#endDate1} AND :#{#endDate2}
                OR pe.exerciseGroup.exam.endDate BETWEEN :#{#endDate1} AND :#{#endDate2}
            """)
    List<ProgrammingExercise> findAllWithStudentParticipationByRecentEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.studentParticipations pep
            LEFT JOIN FETCH pep.submissions s
            WHERE (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    List<ProgrammingExercise> findAllWithEagerParticipationsAndLegalSubmissions();

    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.templateParticipation LEFT JOIN FETCH pe.solutionParticipation")
    List<ProgrammingExercise> findAllWithEagerTemplateAndSolutionParticipations();

    @EntityGraph(type = LOAD, attributePaths = "studentParticipations")
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsById(Long exerciseId);

    @Query("""
            SELECT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.studentParticipations pep
            LEFT JOIN FETCH pep.student
            LEFT JOIN FETCH pep.submissions s
            WHERE pe.id = :#{#exerciseId}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "studentParticipations" })
    Optional<ProgrammingExercise> findWithAllParticipationsById(Long exerciseId);

    ProgrammingExercise findOneByTemplateParticipationId(Long templateParticipationId);

    ProgrammingExercise findOneBySolutionParticipationId(Long solutionParticipationId);

    /**
     * Query which fetches all the programming exercises for which the user is instructor in the course and matching the search criteria.
     * As JPQL doesn't support unions, the distinction for course exercises and exam exercises is made with sub queries.
     *
     * @param partialTitle       exercise title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT pe FROM ProgrammingExercise pe
            WHERE pe.shortName IS NOT NULL AND (
                pe.id IN (
                    SELECT coursePe.id
                    FROM ProgrammingExercise coursePe
                    WHERE coursePe.course.instructorGroupName IN :groups
                        AND (coursePe.title LIKE %:partialTitle% OR coursePe.course.title LIKE %:partialCourseTitle%)
                ) OR pe.id IN (
                    SELECT examPe.id
                    FROM ProgrammingExercise examPe
                    WHERE examPe.exerciseGroup.exam.course.instructorGroupName IN :groups
                        AND (examPe.title LIKE %:partialTitle% OR examPe.exerciseGroup.exam.course.title LIKE %:partialCourseTitle%)
                )
            )
            """)
    Page<ProgrammingExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("groups") Set<String> groups, Pageable pageable);

    Page<ProgrammingExercise> findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(String partialTitle,
            String partialCourseTitle, Pageable pageable);

    @Query("""
            SELECT p FROM ProgrammingExercise p
            LEFT JOIN FETCH p.testCases
            LEFT JOIN FETCH p.staticCodeAnalysisCategories
            LEFT JOIN FETCH p.exerciseHints
            LEFT JOIN FETCH p.templateParticipation
            LEFT JOIN FETCH p.solutionParticipation
            WHERE p.id = :#{#exerciseId}
            """)
    Optional<ProgrammingExercise> findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipations(@Param("exerciseId") Long exerciseId);

    /**
     * Returns the programming exercises that have a buildAndTestStudentSubmissionsAfterDueDate higher than the provided date.
     * This can't be done as a standard query as the property contains the word 'And'.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("SELECT pe FROM ProgrammingExercise pe WHERE pe.buildAndTestStudentSubmissionsAfterDueDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * Returns the programming exercises that have manual assessment enabled and a due date higher than the provided date.
     *
     * @param dateTime ZonedDateTime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("SELECT pe FROM ProgrammingExercise pe WHERE pe.assessmentType <> 'AUTOMATIC' AND pe.dueDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllByManualAssessmentAndDueDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * Returns all programming exercises that have a due date after {@code now} and have tests marked with
     * {@link de.tum.in.www1.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE} but no buildAndTestStudentSubmissionsAfterDueDate.
     *
     * @param now the time after which the due date of the exercise has to be
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN pe.testCases tc
            WHERE pe.dueDate > :#{#now}
                AND pe.buildAndTestStudentSubmissionsAfterDueDate IS NULL
                AND tc.visibility = 'AFTER_DUE_DATE'
            """)
    List<ProgrammingExercise> findAllByDueDateAfterDateWithTestsAfterDueDateWithoutBuildStudentSubmissionsDate(@Param("now") ZonedDateTime now);

    /**
     * Returns the programming exercises that are part of an exam with an end date after than the provided date.
     * This method also fetches the exercise group and exam.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("SELECT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.exerciseGroup eg LEFT JOIN FETCH eg.exam e WHERE e.endDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllWithEagerExamByExamEndDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.id = :#{#exerciseId}
                AND s.submitted = TRUE
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    long countSubmissionsByExerciseIdSubmitted(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                p.exercise.id,
                count(DISTINCT p)
            )
            FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND s.submitted = TRUE
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            GROUP BY p.exercise.id
            """)
    List<ExerciseMapEntry> countSubmissionsByExerciseIdsSubmitted(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    long countLegalSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") Long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseIds the exercise ids we are interested in
     * @return the number of distinct submissions belonging to the exercise id
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                p.exercise.id,
                count(DISTINCT p)
            )
            FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            GROUP BY p.exercise.id
            """)
    List<ExerciseMapEntry> countSubmissionsByExerciseIdsSubmittedIgnoreTestRun(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * needs improvement
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id that are assessed
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            LEFT JOIN p.results r
            WHERE p.exercise.id = :#{#exerciseId}
                AND r.submission.submitted = TRUE
                AND (r.submission.type <> 'ILLEGAL' OR r.submission.type IS NULL)
                AND r.assessor IS NOT NULL
                AND r.completionDate IS NOT NULL
            """)
    long countAssessmentsByExerciseIdSubmitted(@Param("exerciseId") Long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id that are assessed
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            LEFT JOIN p.results r
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.testRun = FALSE
                AND r.submission.submitted = TRUE
                AND (r.submission.type <> 'ILLEGAL' OR r.submission.type IS NULL)
                AND r.assessor IS NOT NULL
                AND r.completionDate IS NOT NULL
                """)
    long countAssessmentsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") Long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param examId the exam id we are interested in
     * @return the number of latest submissions belonging to a participation belonging to the exam id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all (only exercises with manual or semi automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.assessmentType <> 'AUTOMATIC'
                AND p.exercise.exerciseGroup.exam.id = :#{#examId}
                AND s IS NOT EMPTY
                AND (s.type <> 'ILLEGAL' or s.type is null)
            """)
    long countLegalSubmissionsByExamIdSubmitted(@Param("examId") Long examId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all (only exercises with manual or semi automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.assessmentType <> 'AUTOMATIC'
                AND p.exercise.course.id = :#{#courseId}
                AND s.submitted = TRUE
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    long countLegalSubmissionsByCourseIdSubmitted(@Param("courseId") Long courseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param exerciseIds the exercise ids of the course we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true (only exercises with manual or semi automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.assessmentType <> 'AUTOMATIC'
                AND p.exercise.id IN :exerciseIds
                AND s.submitted = TRUE
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    long countAllSubmissionsByExerciseIdsSubmitted(@Param("exerciseIds") Set<Long> exerciseIds);

    List<ProgrammingExercise> findAllByCourse_InstructorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse_TeachingAssistantGroupNameIn(Set<String> groupNames);

    @Query("SELECT pe FROM ProgrammingExercise pe WHERE pe.course.instructorGroupName IN :#{#groupNames} OR pe.course.teachingAssistantGroupName IN :#{#groupNames}")
    List<ProgrammingExercise> findAllByInstructorOrTAGroupNameIn(@Param("groupNames") Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse(Course course);

    long countByShortNameAndCourse(String shortName, Course course);

    long countByTitleAndCourse(String shortName, Course course);

    long countByShortNameAndExerciseGroupExamCourse(String shortName, Course course);

    long countByTitleAndExerciseGroupExamCourse(String shortName, Course course);

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in future.
     *
     * @return List<ProgrammingExercise>
     */
    default List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    default ProgrammingExercise getExercise(TemplateProgrammingExerciseParticipation participation) {
        return findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    default ProgrammingExercise getExercise(SolutionProgrammingExerciseParticipation participation) {
        return findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * Find a programming exercise by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdElseThrow(Long programmingExerciseId) throws EntityNotFoundException {
        return findById(programmingExerciseId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, including template and solution but without results.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationElseThrow(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, including template and solution participation and their latest results.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationLatestResultById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded studentParticipations and submissions
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * @param exerciseId     the exercise we are interested in
     * @param ignoreTestRuns should be set for exam exercises
     * @return the number of programming submissions which should be assessed
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countLegalSubmissionsByExerciseIdSubmitted(Long exerciseId, boolean ignoreTestRuns) {
        if (ignoreTestRuns) {
            return countLegalSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
        }
        else {
            return countSubmissionsByExerciseIdSubmitted(exerciseId);
        }
    }

    /**
     * @param exerciseId     the exercise we are interested in
     * @param ignoreTestRuns should be set for exam exercises
     * @return the number of assessed programming submissions
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countAssessmentsByExerciseIdSubmitted(Long exerciseId, boolean ignoreTestRuns) {
        if (ignoreTestRuns) {
            return countAssessmentsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
        }
        else {
            return countAssessmentsByExerciseIdSubmitted(exerciseId);
        }
    }
}
