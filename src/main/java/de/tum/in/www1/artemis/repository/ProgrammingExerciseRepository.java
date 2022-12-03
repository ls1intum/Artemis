package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.config.Constants.TITLE_NAME_PATTERN;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.hibernate.Hibernate;
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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.templateParticipation tp
            LEFT JOIN FETCH pe.solutionParticipation sp
            LEFT JOIN FETCH tp.results tpr
            LEFT JOIN FETCH sp.results spr
            LEFT JOIN FETCH pe.categories
            WHERE pe.course.id = :#{#courseId}
                AND (tpr.id = (SELECT MAX(re1.id) FROM tp.results re1) OR tpr.id IS NULL)
                AND (spr.id = (SELECT MAX(re2.id) FROM sp.results re2) OR spr.id IS NULL)
            """)
    List<ProgrammingExercise> findByCourseIdWithLatestResultForTemplateSolutionParticipations(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "auxiliaryRepositories",
            "submissionPolicy" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "auxiliaryRepositories" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig", "templateParticipation.submissions.results", "solutionParticipation.submissions.results" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationSubmissionsAndResultsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "testCases")
    Optional<ProgrammingExercise> findWithTestCasesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "auxiliaryRepositories")
    Optional<ProgrammingExercise> findWithAuxiliaryRepositoriesById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "submissionPolicy")
    Optional<ProgrammingExercise> findWithSubmissionPolicyById(Long exerciseId);

    /**
     * Returns all programming exercises with its test cases
     * @return all programming exercises
     */
    @Query("SELECT p FROM ProgrammingExercise p LEFT JOIN FETCH p.testCases")
    Set<ProgrammingExercise> findAllWithEagerTestCases();

    /**
     * Returns all programming exercises with their hints and tasks
     * @return all programming exercises
     */
    @Query("""
            SELECT p FROM ProgrammingExercise p
            LEFT JOIN FETCH p.tasks
            LEFT JOIN FETCH p.exerciseHints
            """)
    Set<ProgrammingExercise> findAllWithEagerTasksAndHints();

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
                AND (tpr.id = (SELECT MAX(re1.id) FROM tp.results re1) OR tpr.id IS NULL)
                AND (spr.id = (SELECT MAX(re2.id) FROM sp.results re2) OR spr.id IS NULL)
            """)
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationLatestResultById(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.studentParticipations")
    List<ProgrammingExercise> findAllWithEagerParticipations();

    /**
     * Get all programming exercises that need to be scheduled: Those must satisfy one of the following requirements:
     * <ul>
     * <li>The release date is in the future → Schedule combine template commits</li>
     * <li>The build and test student submissions after deadline date is in the future</li>
     * <li>The due date is in the future</li>
     * <li>There are participations in the exercise with individual due dates in the future</li>
     * </ul>
     *
     * @param now the current time
     * @return List of the exercises that should be scheduled
     */
    @Query("""
            select distinct pe from ProgrammingExercise pe
            left join pe.studentParticipations participation
            where pe.releaseDate > :#{#now}
                or pe.buildAndTestStudentSubmissionsAfterDueDate > :#{#now}
                or pe.dueDate > :#{#now}
                or (participation.individualDueDate is not null and participation.individualDueDate > :#{#now})
            """)
    List<ProgrammingExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            WHERE pe.course is not null
                AND :#{#endDate1} <= pe.course.endDate
                AND pe.course.endDate <= :#{#endDate2}
            """)
    List<ProgrammingExercise> findAllByRecentCourseEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            WHERE pe.exerciseGroup is not null
                AND :#{#endDate1} <= pe.exerciseGroup.exam.endDate
                AND pe.exerciseGroup.exam.endDate <= :#{#endDate2}
            """)
    List<ProgrammingExercise> findAllByRecentExamEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.studentParticipations
            WHERE pe.dueDate is not null
                AND :#{#endDate1} <= pe.dueDate
                AND pe.dueDate <= :#{#endDate2}
            """)
    List<ProgrammingExercise> findAllWithStudentParticipationByRecentDueDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe FROM ProgrammingExercise pe
            LEFT JOIN FETCH pe.studentParticipations
            WHERE pe.exerciseGroup is not null
                AND :#{#endDate1} <= pe.exerciseGroup.exam.endDate
                AND pe.exerciseGroup.exam.endDate <= :#{#endDate2}
            """)
    List<ProgrammingExercise> findAllWithStudentParticipationByRecentExamEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

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
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "studentParticipations" })
    Optional<ProgrammingExercise> findWithAllParticipationsById(Long exerciseId);

    @Query("""
            SELECT pe FROM ProgrammingExercise pe
            LEFT JOIN pe.studentParticipations pep
            WHERE pep.id = :#{#participationId}
                OR pe.templateParticipation.id = :#{#participationId}
                OR pe.solutionParticipation.id = :#{#participationId}
            """)
    Optional<ProgrammingExercise> findByParticipationId(@Param("participationId") Long participationId);

    @Query("""
                SELECT exercise FROM ProgrammingExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM ProgrammingExercise courseExercise
                    WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
                    AND (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM ProgrammingExercise examExercise
                    WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<ProgrammingExercise> queryBySearchTermInAllCoursesAndExamsWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups,
            Pageable pageable);

    @Query("""
            SELECT courseExercise FROM ProgrammingExercise courseExercise
            WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
            AND (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%)
                """)
    Page<ProgrammingExercise> queryBySearchTermInAllCoursesWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT examExercise FROM ProgrammingExercise examExercise
            WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
            AND (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)
                """)
    Page<ProgrammingExercise> queryBySearchTermInAllExamsWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT exercise FROM ProgrammingExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM ProgrammingExercise courseExercise
                    WHERE (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM ProgrammingExercise examExercise
                    WHERE (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<ProgrammingExercise> queryBySearchTermInAllCoursesAndExams(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT courseExercise FROM ProgrammingExercise courseExercise
            WHERE (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%)
                """)
    Page<ProgrammingExercise> queryBySearchTermInAllCourses(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT examExercise FROM ProgrammingExercise examExercise
            WHERE (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)
                """)
    Page<ProgrammingExercise> queryBySearchTermInAllExams(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT p FROM ProgrammingExercise p
            LEFT JOIN FETCH p.testCases tc
            LEFT JOIN FETCH p.staticCodeAnalysisCategories
            LEFT JOIN FETCH p.exerciseHints
            LEFT JOIN FETCH p.templateParticipation
            LEFT JOIN FETCH p.solutionParticipation
            LEFT JOIN FETCH p.auxiliaryRepositories
            LEFT JOIN FETCH tc.solutionEntries
                WHERE p.id = :#{#exerciseId}
                """)
    Optional<ProgrammingExercise> findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(@Param("exerciseId") Long exerciseId);

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
     * @return the number of the latest submissions belonging to a participation belonging to the exam id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all (only exercises with manual or semi-automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p
            JOIN p.submissions s
            WHERE p.exercise.assessmentType <> 'AUTOMATIC'
                AND p.exercise.exerciseGroup.exam.id = :#{#examId}
                AND s IS NOT EMPTY
                AND (s.type <> 'ILLEGAL' OR s.type is null)
            """)
    long countLegalSubmissionsByExamIdSubmitted(@Param("examId") Long examId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param exerciseIds the exercise ids of the course we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true (only exercises with manual or semi-automatic correction are considered)
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

    List<ProgrammingExercise> findAllByCourse_EditorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse_TeachingAssistantGroupNameIn(Set<String> groupNames);

    // Note: we have to use left join here to avoid issues in the where clause, there can be at most one indirection (e.g. c1.editorGroupName) in the WHERE clause when using "OR"
    // Multiple different indirection in the WHERE clause (e.g. pe.course.instructorGroupName and ex.course.instructorGroupName) would not work
    @Query("""
            SELECT pe FROM ProgrammingExercise pe LEFT JOIN pe.course c1 LEFT JOIN pe.exerciseGroup eg LEFT JOIN eg.exam ex LEFT JOIN ex.course c2
            WHERE c1.instructorGroupName IN :#{#groupNames}
                OR c1.editorGroupName IN :#{#groupNames}
                OR c1.teachingAssistantGroupName IN :#{#groupNames}
                OR c2.instructorGroupName IN :#{#groupNames}
                OR c2.editorGroupName IN :#{#groupNames}
                OR c2.teachingAssistantGroupName IN :#{#groupNames}
            """)
    List<ProgrammingExercise> findAllByInstructorOrEditorOrTAGroupNameIn(@Param("groupNames") Set<String> groupNames);

    // Note: we have to use left join here to avoid issues in the where clause, see the explanation above
    @Query("""
            SELECT pe FROM ProgrammingExercise pe LEFT JOIN pe.exerciseGroup eg LEFT JOIN eg.exam ex
            WHERE pe.course = :#{#course}
                OR ex.course = :#{#course}
            """)
    List<ProgrammingExercise> findAllProgrammingExercisesInCourseOrInExamsOfCourse(@Param("course") Course course);

    long countByShortNameAndCourse(String shortName, Course course);

    long countByTitleAndCourse(String shortName, Course course);

    long countByShortNameAndExerciseGroupExamCourse(String shortName, Course course);

    long countByTitleAndExerciseGroupExamCourse(String shortName, Course course);

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in the future.
     *
     * @return List<ProgrammingExercise>
     */
    default List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
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
     * Find a programming exercise with auxiliary repositories by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdWithAuxiliaryRepositoriesElseThrow(Long programmingExerciseId) throws EntityNotFoundException {
        return findWithAuxiliaryRepositoriesById(programmingExerciseId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise with the submission policy by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdWithSubmissionPolicyElseThrow(Long programmingExerciseId) throws EntityNotFoundException {
        return findWithSubmissionPolicyById(programmingExerciseId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, including template and solution but without results.
     * TODO: we should remove this method later on and use 'findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow' in all places,
     *  they have same functionality.
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
     * Find a programming exercise by its id, including auxiliary repositories, template and solution participation and
     * their latest results.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation and auxiliary repositories
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
     * @return the number of programming submissions which should be assessed
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countLegalSubmissionsByExerciseIdSubmitted(Long exerciseId) {
        return countLegalSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
    }

    /**
     * @param exerciseId     the exercise we are interested in
     * @return the number of assessed programming submissions
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countAssessmentsByExerciseIdSubmitted(Long exerciseId) {
        return countAssessmentsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation, team assignment config and categories
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation, submissions and results
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationSubmissionsAndResultsElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationSubmissionsAndResultsById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation, and latest result
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationLatestResultElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationLatestResultById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * Retrieve the programming exercise from a programming exercise participation. In case the programming exercise is null or not initialized,
     * this method will load it properly from the database and connect it to the participation
     * @param participation the programming exercise participation for which the programming exercise should be found
     * @return the programming exercise
     */
    @Nullable
    default ProgrammingExercise getProgrammingExerciseFromParticipation(ProgrammingExerciseParticipation participation) {
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            // Find the programming exercise for the given participation
            var optionalProgrammingExercise = findByParticipationId(participation.getId());
            if (optionalProgrammingExercise.isEmpty()) {
                return null;
            }
            participation.setProgrammingExercise(optionalProgrammingExercise.get());
        }
        return participation.getProgrammingExercise();
    }

    /**
     * Validate the programming exercise title.
     * 1. Check presence and length of exercise title
     * 2. Find forbidden patterns in exercise title
     *  @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     */
    default void validateTitle(ProgrammingExercise programmingExercise, Course course) {
        // Check if exercise title is set
        if (programmingExercise.getTitle() == null || programmingExercise.getTitle().length() < 3) {
            throw new BadRequestAlertException("The title of the programming exercise is too short", "Exercise", "programmingExerciseTitleInvalid");
        }

        // Check if the exercise title matches regex
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(programmingExercise.getTitle());
        if (!titleMatcher.matches()) {
            throw new BadRequestAlertException("The title is invalid", "Exercise", "titleInvalid");
        }

        // Check that the exercise title is unique among all programming exercises in the course, otherwise the corresponding project in the VCS system cannot be generated
        long numberOfProgrammingExercisesWithSameTitle = countByTitleAndCourse(programmingExercise.getTitle(), course)
                + countByTitleAndExerciseGroupExamCourse(programmingExercise.getTitle(), course);
        if (numberOfProgrammingExercisesWithSameTitle > 0) {
            throw new BadRequestAlertException("A programming exercise with the same title already exists. Please choose a different title.", "Exercise", "titleAlreadyExists");
        }
    }

    /**
     * Validates the course and programming exercise short name.
     * 1. Check presence and length of exercise short name
     * 2. Check presence and length of course short name
     * 3. Find forbidden patterns in exercise short name
     * 4. Check that the short name doesn't already exist withing course or exam exercises
     *  @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     */
    default void validateCourseAndExerciseShortName(ProgrammingExercise programmingExercise, Course course) {
        // Check if exercise shortname is set
        if (programmingExercise.getShortName() == null || programmingExercise.getShortName().length() < 3) {
            throw new BadRequestAlertException("The shortname of the programming exercise is not set or too short", "Exercise", "programmingExerciseShortnameInvalid");
        }

        // Check if the course shortname is set
        if (course.getShortName() == null || course.getShortName().length() < 3) {
            throw new BadRequestAlertException("The shortname of the course is not set or too short", "Exercise", "courseShortnameInvalid");
        }

        // Check if exercise shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(programmingExercise.getShortName());
        if (!shortNameMatcher.matches()) {
            throw new BadRequestAlertException("The shortname is invalid", "Exercise", "shortnameInvalid");
        }

        // NOTE: we have to cover two cases here: exercises directly stored in the course and exercises indirectly stored in the course (exercise -> exerciseGroup -> exam ->
        // course)
        long numberOfProgrammingExercisesWithSameShortName = countByShortNameAndCourse(programmingExercise.getShortName(), course)
                + countByShortNameAndExerciseGroupExamCourse(programmingExercise.getShortName(), course);
        if (numberOfProgrammingExercisesWithSameShortName > 0) {
            throw new BadRequestAlertException("A programming exercise with the same short name already exists. Please choose a different short name.", "Exercise",
                    "shortnameAlreadyExists");
        }
    }

    /**
     * Validate the general course settings.
     * 1. Validate the title
     * 2. Validate the course and programming exercise short name.
     *  @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     */
    default void validateCourseSettings(ProgrammingExercise programmingExercise, Course course) {
        validateTitle(programmingExercise, course);
        validateCourseAndExerciseShortName(programmingExercise, course);
    }
}
