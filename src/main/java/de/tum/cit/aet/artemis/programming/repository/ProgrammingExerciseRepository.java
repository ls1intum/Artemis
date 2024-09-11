package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.cit.aet.artemis.core.config.Constants.TITLE_NAME_PATTERN;
import static de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.DynamicSpecificationRepository;
import de.tum.cit.aet.artemis.core.repository.base.FetchOptions;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise_;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise_;
import de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseMapEntry;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseRepository extends DynamicSpecificationRepository<ProgrammingExercise, Long, ProgrammingExerciseFetchOptions> {

    /**
     * Does a max join on the result table for each participation by result id (the newer the result id, the newer the result).
     * This makes sure that we only receive the latest result for the template and the solution participation if they exist.
     *
     * @param courseId the course the returned programming exercises belong to.
     * @return all exercises for the given course with only the latest results for solution and template each (if present).
     */
    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.templateParticipation tp
                LEFT JOIN FETCH pe.solutionParticipation sp
                LEFT JOIN FETCH tp.results tpr
                LEFT JOIN FETCH sp.results spr
                LEFT JOIN FETCH pe.categories
            WHERE pe.course.id = :courseId
                AND (tpr.id = (SELECT MAX(re1.id) FROM tp.results re1) OR tpr.id IS NULL)
                AND (spr.id = (SELECT MAX(re2.id) FROM sp.results re2) OR spr.id IS NULL)
            """)
    List<ProgrammingExercise> findByCourseIdWithLatestResultForTemplateSolutionParticipations(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "auxiliaryRepositories",
            "submissionPolicy" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "auxiliaryRepositories",
            "submissionPolicy", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "competencies", "auxiliaryRepositories",
            "submissionPolicy" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "competencies", "auxiliaryRepositories",
            "submissionPolicy", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "competencies", "auxiliaryRepositories",
            "submissionPolicy", "plagiarismDetectionConfig", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigById(
            long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "auxiliaryRepositories" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "auxiliaryRepositories", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig", "templateParticipation.submissions.results", "solutionParticipation.submissions.results",
            "auxiliaryRepositories", "plagiarismDetectionConfig", "templateParticipation", "solutionParticipation", "buildConfig" })
    Optional<ProgrammingExercise> findForCreationById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "testCases")
    Optional<ProgrammingExercise> findWithTestCasesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "auxiliaryRepositories")
    Optional<ProgrammingExercise> findWithAuxiliaryRepositoriesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "auxiliaryRepositories", "competencies", "buildConfig" })
    Optional<ProgrammingExercise> findWithAuxiliaryRepositoriesCompetenciesAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "submissionPolicy")
    Optional<ProgrammingExercise> findWithSubmissionPolicyById(long exerciseId);

    List<ProgrammingExercise> findAllByProjectKey(String projectKey);

    @EntityGraph(type = LOAD, attributePaths = "submissionPolicy")
    List<ProgrammingExercise> findWithSubmissionPolicyByProjectKey(String projectKey);

    /**
     * Finds one programming exercise including its submission policy by the exercise's project key.
     *
     * @param projectKey           the project key of the programming exercise.
     * @param withSubmissionPolicy whether the submission policy should be included in the result.
     * @return the programming exercise.
     * @throws EntityNotFoundException if no programming exercise or multiple exercises with the given project key exist.
     */
    default ProgrammingExercise findOneByProjectKeyOrThrow(String projectKey, boolean withSubmissionPolicy) throws EntityNotFoundException {
        List<ProgrammingExercise> exercises;
        if (withSubmissionPolicy) {
            exercises = findWithSubmissionPolicyByProjectKey(projectKey);
        }
        else {
            exercises = findAllByProjectKey(projectKey);
        }
        if (exercises.size() != 1) {
            throw new EntityNotFoundException("No exercise or multiple exercises found for the given project key: " + projectKey);
        }
        return exercises.getFirst();
    }

    /**
     * Get a programmingExercise with template participation, each with the latest result and feedbacks.
     * NOTICE: this query is quite expensive because it loads all feedback and test cases, and it includes sub queries to retrieve the latest result
     * IMPORTANT: you should generally avoid using this query except you really need all information!!
     *
     * @param exerciseId the id of the exercise that should be fetched.
     * @return the exercise with the given ID, if found.
     */
    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.templateParticipation tp
                LEFT JOIN FETCH tp.results AS tpr
                LEFT JOIN FETCH tpr.feedbacks tf
                LEFT JOIN FETCH tf.testCase
                LEFT JOIN FETCH tpr.submission
            WHERE pe.id = :exerciseId
                AND (tpr.id = (SELECT MAX(re1.id) FROM tp.results re1) OR tpr.id IS NULL)
            """)
    Optional<ProgrammingExercise> findWithTemplateParticipationLatestResultFeedbackTestCasesById(@Param("exerciseId") long exerciseId);

    /**
     * Get a programmingExercise with solution participation, each with the latest result and feedbacks.
     * NOTICE: this query is quite expensive because it loads all feedback and test cases, and it includes sub queries to retrieve the latest result
     * IMPORTANT: you should generally avoid using this query except you really need all information!!
     *
     * @param exerciseId the id of the exercise that should be fetched.
     * @return the exercise with the given ID, if found.
     */
    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.solutionParticipation sp
                LEFT JOIN FETCH sp.results AS spr
                LEFT JOIN FETCH spr.feedbacks sf
                LEFT JOIN FETCH sf.testCase
                LEFT JOIN FETCH spr.submission
            WHERE pe.id = :exerciseId
                AND (spr.id = (SELECT MAX(re2.id) FROM sp.results re2) OR spr.id IS NULL)
            """)
    Optional<ProgrammingExercise> findWithSolutionParticipationLatestResultFeedbackTestCasesById(@Param("exerciseId") long exerciseId);

    /**
     * Get all programming exercises that need to be scheduled: Those must satisfy one of the following requirements:
     * <ul>
     * <li>The release date is in the future → Schedule combine template commits</li>
     * <li>The build and test student submissions after due date is in the future</li>
     * <li>The due date is in the future</li>
     * <li>There are participations in the exercise with individual due dates in the future</li>
     * </ul>
     *
     * @param now the current time
     * @return List of the exercises that should be scheduled
     */
    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.studentParticipations participation
                LEFT JOIN FETCH participation.team team
                LEFT JOIN FETCH team.students
            WHERE pe.releaseDate > :now
                OR pe.buildAndTestStudentSubmissionsAfterDueDate > :now
                OR pe.dueDate > :now
                OR (participation.individualDueDate IS NOT NULL AND participation.individualDueDate > :now)
            """)
    List<ProgrammingExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
            WHERE pe.course IS NOT NULL
                AND :endDate1 <= pe.course.endDate
                AND pe.course.endDate <= :endDate2
            """)
    List<ProgrammingExercise> findAllByRecentCourseEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
            WHERE pe.exerciseGroup IS NOT NULL
                AND :endDate1 <= pe.exerciseGroup.exam.endDate
                AND pe.exerciseGroup.exam.endDate <= :endDate2
            """)
    List<ProgrammingExercise> findAllByRecentExamEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.studentParticipations
            WHERE pe.dueDate IS NOT NULL
                AND :endDate1 <= pe.dueDate
                AND pe.dueDate <= :endDate2
            """)
    List<ProgrammingExercise> findAllWithStudentParticipationByRecentDueDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.studentParticipations
            WHERE pe.exerciseGroup IS NOT NULL
                AND :endDate1 <= pe.exerciseGroup.exam.endDate
                AND pe.exerciseGroup.exam.endDate <= :endDate2
            """)
    List<ProgrammingExercise> findAllWithStudentParticipationByRecentExamEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.templateParticipation
                LEFT JOIN FETCH pe.solutionParticipation
            WHERE pe.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findWithEagerTemplateAndSolutionParticipationsById(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.team", "studentParticipations.team.students" })
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsById(long exerciseId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.studentParticipations pep
                LEFT JOIN FETCH pep.student
                LEFT JOIN FETCH pep.team t
            LEFT JOIN FETCH t.students
            LEFT JOIN FETCH pep.submissions s
            WHERE pe.id = :exerciseId
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "studentParticipations.team.students", "buildConfig" })
    Optional<ProgrammingExercise> findWithAllParticipationsAndBuildConfigById(long exerciseId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN pe.studentParticipations spep
            WHERE spep.id = :participationId
            """)
    Optional<ProgrammingExercise> findByStudentParticipationId(@Param("participationId") long participationId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
            WHERE pe.templateParticipation.id = :participationId
            """)
    Optional<ProgrammingExercise> findByTemplateParticipationId(@Param("participationId") long participationId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.buildConfig
            WHERE pe.solutionParticipation.id = :participationId
            """)
    Optional<ProgrammingExercise> findBySolutionParticipationIdWithBuildConfig(@Param("participationId") long participationId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN pe.studentParticipations spep
                LEFT JOIN FETCH pe.buildConfig
            WHERE spep.id = :participationId
            """)
    Optional<ProgrammingExercise> findByStudentParticipationIdWithBuildConfig(@Param("participationId") long participationId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.buildConfig
            WHERE pe.templateParticipation.id = :participationId
            """)
    Optional<ProgrammingExercise> findByTemplateParticipationIdWithBuildConfig(@Param("participationId") long participationId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
            WHERE pe.solutionParticipation.id = :participationId
            """)
    Optional<ProgrammingExercise> findBySolutionParticipationId(@Param("participationId") long participationId);

    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN pe.studentParticipations pep
                LEFT JOIN FETCH pe.templateParticipation tp
            WHERE pep.id = :participationId
            """)
    Optional<ProgrammingExercise> findByStudentParticipationIdWithTemplateParticipation(@Param("participationId") long participationId);

    @Query("""
            SELECT p
            FROM ProgrammingExercise p
                LEFT JOIN FETCH p.testCases tc
                LEFT JOIN FETCH p.staticCodeAnalysisCategories
                LEFT JOIN FETCH p.exerciseHints
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH tc.solutionEntries
                LEFT JOIN FETCH p.buildConfig
            WHERE p.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxReposAndBuildConfig(
            @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT p
            FROM ProgrammingExercise p
                LEFT JOIN FETCH p.testCases tc
                LEFT JOIN FETCH p.staticCodeAnalysisCategories
                LEFT JOIN FETCH p.exerciseHints
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH tc.solutionEntries
                LEFT JOIN FETCH p.buildConfig
            WHERE p.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdWithEagerBuildConfigTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(
            @Param("exerciseId") long exerciseId);

    /**
     * Returns all programming exercises that have a due date after {@code now} and have tests marked with
     * {@link de.tum.cit.aet.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE} but no buildAndTestStudentSubmissionsAfterDueDate.
     *
     * @param now the time after which the due date of the exercise has to be
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN pe.testCases tc
            WHERE pe.dueDate > :now
                AND pe.buildAndTestStudentSubmissionsAfterDueDate IS NULL
                AND tc.visibility = de.tum.cit.aet.artemis.domain.enumeration.Visibility.AFTER_DUE_DATE
            """)
    List<ProgrammingExercise> findAllByDueDateAfterDateWithTestsAfterDueDateWithoutBuildStudentSubmissionsDate(@Param("now") ZonedDateTime now);

    /**
     * Returns the programming exercises that are part of an exam with an end date after than the provided date.
     * This method also fetches the exercise group and exam.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.exerciseGroup eg
                LEFT JOIN FETCH eg.exam e
            WHERE e.endDate > :dateTime
            """)
    List<ProgrammingExercise> findAllWithEagerExamByExamEndDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the due date.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id
     */
    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    long countLegalSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the due date.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseIds the exercise ids we are interested in
     * @return list of exercises with the count of distinct submissions belonging to the exercise id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                p.exercise.id,
                count(DISTINCT p)
            )
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            GROUP BY p.exercise.id
            """)
    List<ExerciseMapEntry> countSubmissionsByExerciseIdsSubmittedIgnoreTestRun(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the due date.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id that are assessed
     */
    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN p.results r
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND r.submission.submitted = TRUE
                AND (r.submission.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR r.submission.type IS NULL)
                AND r.assessor IS NOT NULL
                AND r.completionDate IS NOT NULL
            """)
    long countAssessmentsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the due date.
     *
     * @param examId the exam id we are interested in
     * @return the number of the latest submissions belonging to a participation belonging to the exam id, which have the submitted flag set to true and the submission date before
     *         the exercise due date, or no exercise due date at all (only exercises with manual or semi-automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.assessmentType <> de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.AUTOMATIC
                AND p.exercise.exerciseGroup.exam.id = :examId
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    long countLegalSubmissionsByExamIdSubmitted(@Param("examId") long examId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the due date.
     *
     * @param exerciseIds the exercise ids of the course we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true (only exercises with manual or semi-automatic correction are
     *         considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.assessmentType <> de.tum.cit.aet.artemis.domain.enumeration.AssessmentType.AUTOMATIC
                AND p.exercise.id IN :exerciseIds
                AND s.submitted = TRUE
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    long countAllSubmissionsByExerciseIdsSubmitted(@Param("exerciseIds") Set<Long> exerciseIds);

    List<ProgrammingExercise> findAllByCourse_InstructorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse_EditorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse_TeachingAssistantGroupNameIn(Set<String> groupNames);

    // Note: we have to use left join here to avoid issues in the where clause, there can be at most one indirection (e.g. c1.editorGroupName) in the WHERE clause when using "OR"
    // Multiple different indirection in the WHERE clause (e.g. pe.course.instructorGroupName and ex.course.instructorGroupName) would not work
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN pe.course c1
                LEFT JOIN pe.exerciseGroup eg
                LEFT JOIN eg.exam ex
                LEFT JOIN ex.course c2
            WHERE c1.instructorGroupName IN :groupNames
                OR c1.editorGroupName IN :groupNames
                OR c1.teachingAssistantGroupName IN :groupNames
                OR c2.instructorGroupName IN :groupNames
                OR c2.editorGroupName IN :groupNames
                OR c2.teachingAssistantGroupName IN :groupNames
            """)
    List<ProgrammingExercise> findAllByInstructorOrEditorOrTAGroupNameIn(@Param("groupNames") Set<String> groupNames);

    // Note: we have to use left join here to avoid issues in the where clause, see the explanation above
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN pe.exerciseGroup eg
                LEFT JOIN eg.exam ex
            WHERE pe.course = :course
                OR ex.course = :course
            """)
    List<ProgrammingExercise> findAllProgrammingExercisesInCourseOrInExamsOfCourse(@Param("course") Course course);

    @EntityGraph(type = LOAD, attributePaths = { "plagiarismDetectionConfig", "teamAssignmentConfig", "buildConfig" })
    Optional<ProgrammingExercise> findWithPlagiarismDetectionConfigTeamConfigAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "buildConfig" })
    Optional<ProgrammingExercise> findWithBuildConfigById(long exerciseId);

    long countByShortNameAndCourse(String shortName, Course course);

    long countByTitleAndCourse(String shortName, Course course);

    long countByShortNameAndExerciseGroupExamCourse(String shortName, Course course);

    long countByTitleAndExerciseGroupExamCourse(String shortName, Course course);

    /**
     * Find a programming exercise by its id, with grading criteria loaded, and throw an EntityNotFoundException if it cannot be found
     *
     * @param exerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @Query("""
            SELECT DISTINCT e
            FROM ProgrammingExercise e
                LEFT JOIN FETCH e.gradingCriteria
            WHERE e.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdWithGradingCriteria(@Param("exerciseId") long exerciseId);

    default ProgrammingExercise findByIdWithGradingCriteriaElseThrow(long exerciseId) {
        return getValueElseThrow(findByIdWithGradingCriteria(exerciseId), exerciseId);
    }

    /**
     * Find a programming exercise by its id and fetch related plagiarism detection config and team config.
     * Throws an EntityNotFoundException if the exercise cannot be found.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdWithPlagiarismDetectionConfigTeamConfigAndBuildConfigElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithPlagiarismDetectionConfigTeamConfigAndBuildConfigById(programmingExerciseId), programmingExerciseId);
    }

    @NotNull
    default ProgrammingExercise findByIdWithBuildConfigElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithBuildConfigById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise with auxiliary repositories by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdWithAuxiliaryRepositoriesElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithAuxiliaryRepositoriesById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise with auxiliary repositories competencies, and buildConfig by its id and throw an {@link EntityNotFoundException} if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdWithAuxiliaryRepositoriesCompetenciesAndBuildConfigElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithAuxiliaryRepositoriesCompetenciesAndBuildConfigById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise with the submission policy by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NotNull
    default ProgrammingExercise findByIdWithSubmissionPolicyElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithSubmissionPolicyById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id, including template and solution but without results.
     * TODO: we should remove this method later on and use 'findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow' in all places,
     * they have same functionality.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    // TODO: rename, this method does more than it promises
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId), programmingExerciseId);
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
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id, including auxiliary repositories, template and solution participation,
     * their latest results and build config.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesAndBuildConfigElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesAndBuildConfigById(programmingExerciseId);
        return getValueElseThrow(programmingExercise, programmingExerciseId);
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
        return getValueElseThrow(findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of programming submissions which should be assessed
     *         We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countLegalSubmissionsByExerciseIdSubmitted(long exerciseId) {
        return countLegalSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of assessed programming submissions
     *         We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countAssessmentsByExerciseIdSubmitted(long exerciseId) {
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
        return getValueElseThrow(findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation, team assignment config, categories and build config
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId);
        return getValueElseThrow(programmingExercise, programmingExerciseId);
    }

    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        return getValueElseThrow(findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesById(programmingExerciseId), programmingExerciseId);
    }

    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigById(
                programmingExerciseId);
        return getValueElseThrow(programmingExercise, programmingExerciseId);
    }

    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigElseThrow(
            long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(
                findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigById(programmingExerciseId),
                programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded objects required for the creation of a programming exercise.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findForCreationByIdElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findForCreationById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Saves the given programming exercise to the database.
     * <p>
     * When saving a programming exercise Hibernates returns an exercise with references to proxy objects.
     * Thus, we need to load the objects referenced by the programming exercise again.
     *
     * @param exercise The programming exercise that should be saved.
     * @return The saved programming exercise.
     */
    default ProgrammingExercise saveForCreation(ProgrammingExercise exercise) {
        this.saveAndFlush(exercise);
        return this.findForCreationByIdElseThrow(exercise.getId());
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded template and solution participation,
     * including the latest result with feedback and test cases.
     * <p>
     * NOTICE: this query is quite expensive because it loads all feedback and test cases,
     * and it includes sub queries to retrieve the latest result
     * IMPORTANT: you should generally avoid using this query except you really need all information!!
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationLatestResultFeedbackTestCasesElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        // TODO: This is a dark hack. Move this into a service where we properly load only the solution participation in the second step
        ProgrammingExercise programmingExerciseWithTemplate = getValueElseThrow(findWithTemplateParticipationLatestResultFeedbackTestCasesById(programmingExerciseId),
                programmingExerciseId);
        ProgrammingExercise programmingExerciseWithSolution = getValueElseThrow(findWithSolutionParticipationLatestResultFeedbackTestCasesById(programmingExerciseId),
                programmingExerciseId);

        programmingExerciseWithTemplate.setSolutionParticipation(programmingExerciseWithSolution.getSolutionParticipation());

        return programmingExerciseWithTemplate;
    }

    @NotNull
    default ProgrammingExercise findWithEagerStudentParticipationsByIdElseThrow(long programmingExerciseId) {
        return getValueElseThrow(findWithEagerStudentParticipationsById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Retrieves the associated ProgrammingExercise for a given ProgrammingExerciseParticipation.
     * If the ProgrammingExercise is not already loaded, it is fetched from the database and linked
     * to the specified participation. This method handles different types of participation
     * (template, solution, student) to optimize database queries and avoid performance bottlenecks.
     *
     * @param participation the programming exercise participation object; must not be null
     * @return the linked ProgrammingExercise, or null if not found or the participation is not initialized
     */
    @Nullable
    default ProgrammingExercise getProgrammingExerciseFromParticipation(ProgrammingExerciseParticipation participation) {
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            // Find the programming exercise for the given participation
            // NOTE: we use different methods to find the programming exercise based on the participation type on purpose to avoid slow database queries
            long participationId = participation.getId();
            Optional<ProgrammingExercise> optionalProgrammingExercise = switch (participation) {
                case TemplateProgrammingExerciseParticipation ignored -> findByTemplateParticipationId(participationId);
                case SolutionProgrammingExerciseParticipation ignored -> findBySolutionParticipationId(participationId);
                case ProgrammingExerciseStudentParticipation ignored -> findByStudentParticipationId(participationId);
                default -> Optional.empty();
            };
            if (optionalProgrammingExercise.isEmpty()) {
                return null;
            }
            participation.setProgrammingExercise(optionalProgrammingExercise.get());
        }
        return participation.getProgrammingExercise();
    }

    /**
     * Retrieves the associated ProgrammingExercise with the build config for a given ProgrammingExerciseParticipation.
     * If the ProgrammingExercise is not already loaded, it is fetched from the database and linked
     * to the specified participation. This method handles different types of participation
     * (template, solution, student) to optimize database queries and avoid performance bottlenecks.
     *
     * @param participation the programming exercise participation object; must not be null
     * @return the linked ProgrammingExercise, or null if not found or the participation is not initialized
     */
    default ProgrammingExercise getProgrammingExerciseWithBuildConfigFromParticipation(ProgrammingExerciseParticipation participation) {
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            // Find the programming exercise for the given participation
            // NOTE: we use different methods to find the programming exercise based on the participation type on purpose to avoid slow database queries
            long participationId = participation.getId();
            Optional<ProgrammingExercise> optionalProgrammingExercise = switch (participation) {
                case TemplateProgrammingExerciseParticipation ignored -> findByTemplateParticipationIdWithBuildConfig(participationId);
                case SolutionProgrammingExerciseParticipation ignored -> findBySolutionParticipationIdWithBuildConfig(participationId);
                case ProgrammingExerciseStudentParticipation ignored -> findByStudentParticipationIdWithBuildConfig(participationId);
                default -> Optional.empty();
            };
            if (optionalProgrammingExercise.isEmpty()) {
                return null;
            }
            participation.setProgrammingExercise(optionalProgrammingExercise.get());
        }
        return participation.getProgrammingExercise();
    }

    /**
     * Retrieve the programming exercise from a programming exercise participation.
     *
     * @param participation The programming exercise participation for which to retrieve the programming exercise.
     * @return The programming exercise of the provided participation.
     */
    @NotNull
    default ProgrammingExercise getProgrammingExerciseFromParticipationElseThrow(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise programmingExercise = getProgrammingExerciseFromParticipation(participation);
        if (programmingExercise == null) {
            throw new EntityNotFoundException("No programming exercise found for the participation with id " + participation.getId());
        }
        return programmingExercise;
    }

    /**
     * Fetch the programming exercise with the build config, or throw an EntityNotFoundException if it cannot be found.
     *
     * @param programmingExercise The programming exercise to fetch the build config for.
     * @return The programming exercise with the build config.
     */
    default ProgrammingExercise getProgrammingExerciseWithBuildConfigElseThrow(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getBuildConfig() == null || !Hibernate.isInitialized(programmingExercise.getBuildConfig())) {
            return getValueElseThrow(findWithBuildConfigById(programmingExercise.getId()), programmingExercise.getId());
        }
        return programmingExercise;
    }

    /**
     * Validate the programming exercise title.
     * 1. Check presence and length of exercise title
     * 2. Find forbidden patterns in exercise title
     *
     * @param programmingExercise Programming exercise to be validated
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
     *
     * @param programmingExercise Programming exercise to be validated
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
     *
     * @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     */
    default void validateCourseSettings(ProgrammingExercise programmingExercise, Course course) {
        validateTitle(programmingExercise, course);
        validateCourseAndExerciseShortName(programmingExercise, course);
    }

    /**
     * Fetch options for the {@link ProgrammingExercise} entity.
     * Each option specifies an entity or a collection of entities to fetch eagerly when using a dynamic fetching query.
     */
    enum ProgrammingExerciseFetchOptions implements FetchOptions {

        // @formatter:off
        Categories(Exercise_.CATEGORIES),
        TeamAssignmentConfig(Exercise_.TEAM_ASSIGNMENT_CONFIG),
        AuxiliaryRepositories(ProgrammingExercise_.AUXILIARY_REPOSITORIES),
        GradingCriteria(Exercise_.GRADING_CRITERIA),
        StudentParticipations(ProgrammingExercise_.STUDENT_PARTICIPATIONS),
        TemplateParticipation(ProgrammingExercise_.TEMPLATE_PARTICIPATION),
        SolutionParticipation(ProgrammingExercise_.SOLUTION_PARTICIPATION),
        TestCases(ProgrammingExercise_.TEST_CASES),
        Tasks(ProgrammingExercise_.TASKS),
        StaticCodeAnalysisCategories(ProgrammingExercise_.STATIC_CODE_ANALYSIS_CATEGORIES),
        SubmissionPolicy(ProgrammingExercise_.SUBMISSION_POLICY),
        ExerciseHints(ProgrammingExercise_.EXERCISE_HINTS),
        Competencies(ProgrammingExercise_.COMPETENCIES),
        Teams(ProgrammingExercise_.TEAMS),
        TutorParticipations(ProgrammingExercise_.TUTOR_PARTICIPATIONS),
        ExampleSubmissions(ProgrammingExercise_.EXAMPLE_SUBMISSIONS),
        Attachments(ProgrammingExercise_.ATTACHMENTS),
        Posts(ProgrammingExercise_.POSTS),
        PlagiarismCases(ProgrammingExercise_.PLAGIARISM_CASES),
        PlagiarismDetectionConfig(ProgrammingExercise_.PLAGIARISM_DETECTION_CONFIG);
        // @formatter:on

        private final String fetchPath;

        ProgrammingExerciseFetchOptions(String fetchPath) {
            this.fetchPath = fetchPath;
        }

        public String getFetchPath() {
            return fetchPath;
        }
    }
}
