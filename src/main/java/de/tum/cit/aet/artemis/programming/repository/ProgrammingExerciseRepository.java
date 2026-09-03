package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.cit.aet.artemis.core.config.Constants.TITLE_NAME_PATTERN;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.DynamicSpecificationRepository;
import de.tum.cit.aet.artemis.core.repository.base.FetchOptions;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.deimos.dto.DeimosExerciseScopeInfoDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise_;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise_;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseNamesDTO;
import de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyValuesDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ProgrammingExerciseRepository extends DynamicSpecificationRepository<ProgrammingExercise, Long, ProgrammingExerciseFetchOptions> {

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateParticipationAndBuildConfigById(long exerciseId);

    /**
     * Loads a programming exercise with everything the build trigger reads off it.
     * <p>
     * The trigger otherwise resolves the build config and the auxiliary repositories with a query each, per push, for
     * what are per-exercise values. Both of their loaders return the association when it is already initialized, so one
     * load here removes both queries without introducing anything that has to be invalidated.
     *
     * @param exerciseId the id of the programming exercise
     * @return the exercise with its build config and auxiliary repositories
     */
    @EntityGraph(type = LOAD, attributePaths = { "buildConfig", "auxiliaryRepositories" })
    Optional<ProgrammingExercise> findWithBuildConfigAndAuxiliaryRepositoriesById(long exerciseId);

    /**
     * Returns the values of the exercise's submission policy, without the exercise the policy points back at.
     * <p>
     * Deliberately a projection. The policy's back reference to its exercise is an eager inverse one-to-one, so loading
     * the policy as an entity, or loading the exercise again to read it off there, fetches the whole exercise and the
     * course it eagerly brings along. Grading reads a limit, a flag and possibly a penalty. See
     * {@link SubmissionPolicyValuesDTO}.
     *
     * @param exerciseId the exercise whose submission policy should be read
     * @return the values of the policy, or empty if the exercise has none
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyValuesDTO(
                policy.id,
                CASE
                    WHEN TYPE(policy) = LockRepositoryPolicy THEN 'LOCK_REPOSITORY'
                    WHEN TYPE(policy) = SubmissionPenaltyPolicy THEN 'SUBMISSION_PENALTY'
                    ELSE 'UNKNOWN'
                END,
                policy.submissionLimit,
                policy.active,
                TREAT (policy AS SubmissionPenaltyPolicy).exceedingPenalty)
            FROM ProgrammingExercise exercise
                JOIN exercise.submissionPolicy policy
            WHERE exercise.id = :exerciseId
            """)
    Optional<SubmissionPolicyValuesDTO> findSubmissionPolicyValuesByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Sets the flag that marks the exercise as having changed test cases, but only when it does not already hold that
     * value.
     * <p>
     * Deliberately a modifying query. The flag is a single boolean, and reading the exercise in order to change it
     * meant fetching the whole exercise together with the course it eagerly brings along, then merging all of it back,
     * which is two wide statements for one column. Guarding on the current value inside the statement also means the
     * previous value does not have to be read: the affected row count answers whether anything changed. A null in the
     * column counts as false, which is how {@link ProgrammingExercise#getTestCasesChanged()} reads it.
     *
     * @param exerciseId       the exercise whose flag should be set
     * @param testCasesChanged the value to set the flag to
     * @return 1 if the flag was changed, 0 if it already held that value or no such exercise exists
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ProgrammingExercise exercise
            SET exercise.testCasesChanged = :testCasesChanged
            WHERE exercise.id = :exerciseId
                AND COALESCE(exercise.testCasesChanged, FALSE) <> :testCasesChanged
            """)
    int updateTestCasesChanged(@Param("exerciseId") long exerciseId, @Param("testCasesChanged") boolean testCasesChanged);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "auxiliaryRepositories",
            "submissionPolicy" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "auxiliaryRepositories",
            "submissionPolicy", "buildConfig" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "competencyLinks.competency",
            "auxiliaryRepositories", "submissionPolicy" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "competencyLinks.competency",
            "auxiliaryRepositories", "submissionPolicy", "buildConfig", "exerciseVariantGroup" })
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories", "competencyLinks.competency",
            "auxiliaryRepositories", "submissionPolicy", "plagiarismDetectionConfig", "buildConfig", "exerciseVariantGroup" })
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

    @Query("SELECT COALESCE(bc.timeoutSeconds, 0) FROM ProgrammingExercise pe LEFT JOIN pe.buildConfig bc WHERE pe.id = :exerciseId")
    Optional<Integer> findBuildTimeoutSecondsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Internal part of {@link #findForCreationById}: everything except the grading criteria and the competency links.
     * Call {@link #findForCreationById} instead, which completes this graph.
     *
     * @param exerciseId of the programming exercise
     * @return the programming exercise without its grading criteria and competency links
     */
    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig", "templateParticipation.submissions.results", "solutionParticipation.submissions.results",
            "auxiliaryRepositories", "plagiarismDetectionConfig", "templateParticipation", "solutionParticipation", "buildConfig", "submissionPolicy" })
    Optional<ProgrammingExercise> findForCreationMainGraphById(long exerciseId);

    /**
     * Internal part of {@link #findForCreationById}: the grading criteria with their structured instructions.
     *
     * @param exerciseId of the programming exercise
     * @return the programming exercise with its grading criteria initialized
     */
    @EntityGraph(type = LOAD, attributePaths = { "gradingCriteria", "gradingCriteria.structuredGradingInstructions" })
    Optional<ProgrammingExercise> findWithGradingCriteriaAndInstructionsById(long exerciseId);

    /**
     * Internal part of {@link #findForCreationById}: the competency links with their competencies.
     *
     * @param exerciseId of the programming exercise
     * @return the programming exercise with its competency links initialized
     */
    @EntityGraph(type = LOAD, attributePaths = { "competencyLinks", "competencyLinks.competency" })
    Optional<ProgrammingExercise> findWithCompetencyLinksAndCompetenciesById(long exerciseId);

    /**
     * Finds a programming exercise by its id with the whole graph a freshly created (or imported) exercise needs:
     * template and solution participation (with submissions and results), team assignment config, categories, auxiliary
     * repositories, plagiarism detection config, build config, submission policy, grading criteria (with their
     * structured instructions) and competency links.
     * <p>
     * The graph is assembled from three queries instead of one. Join-fetching the grading criteria and the competency
     * links together with the categories, auxiliary repositories and participation submissions would multiply the result
     * set - one row per combination of all of them - and each of those rows repeats every exercise column, including the
     * problem statement. Two extra lookups by primary key are cheaper than that product, and callers still see a single
     * method returning a fully initialized exercise.
     *
     * @param exerciseId of the programming exercise
     * @return the programming exercise with its complete creation graph initialized
     */
    default Optional<ProgrammingExercise> findForCreationById(long exerciseId) {
        Optional<ProgrammingExercise> optionalExercise = findForCreationMainGraphById(exerciseId);
        optionalExercise.ifPresent(exercise -> {
            findWithGradingCriteriaAndInstructionsById(exerciseId).ifPresent(withCriteria -> exercise.setGradingCriteria(withCriteria.getGradingCriteria()));
            findWithCompetencyLinksAndCompetenciesById(exerciseId).ifPresent(withLinks -> exercise.setCompetencyLinks(withLinks.getCompetencyLinks()));
        });
        return optionalExercise;
    }

    @EntityGraph(type = LOAD, attributePaths = "testCases")
    Optional<ProgrammingExercise> findWithTestCasesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "auxiliaryRepositories")
    Optional<ProgrammingExercise> findWithAuxiliaryRepositoriesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "auxiliaryRepositories", "competencyLinks.competency", "buildConfig",
            "categories", "plagiarismDetectionConfig", "gradingCriteria", "gradingCriteria.structuredGradingInstructions", "exampleSubmissions" })
    Optional<ProgrammingExercise> findForUpdateById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "submissionPolicy")
    Optional<ProgrammingExercise> findWithSubmissionPolicyById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "buildConfig")
    Optional<ProgrammingExercise> findWithBuildConfigById(long exerciseId);

    List<ProgrammingExercise> findAllByProjectKey(String projectKey);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "exerciseVariantGroup" })
    List<ProgrammingExercise> findAllWithCategoriesByCourseId(Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "auxiliaryRepositories" })
    List<ProgrammingExercise> findAllWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesByCourseId(long courseId);

    // course is an eager @ManyToOne, so fetching it here saves the secondary select that git authorization would
    // otherwise pay on every request when it reads the course for its role checks
    @EntityGraph(type = LOAD, attributePaths = { "submissionPolicy", "course" })
    List<ProgrammingExercise> findWithSubmissionPolicyByProjectKey(String projectKey);

    @EntityGraph(type = LOAD, attributePaths = "buildConfig")
    List<ProgrammingExercise> findWithBuildConfigByProjectKey(String projectKey);

    @EntityGraph(type = LOAD, attributePaths = { "submissionPolicy", "buildConfig" })
    List<ProgrammingExercise> findWithSubmissionPolicyAndBuildConfigByProjectKey(String projectKey);

    /**
     * Finds one programming exercise including its submission policy by the exercise's project key.
     *
     * @param projectKey           the project key of the programming exercise.
     * @param withSubmissionPolicy whether the submission policy should be included in the result.
     * @param withBuildConfig      whether the build policy should be included in the result.
     * @return the programming exercise.
     * @throws EntityNotFoundException if no programming exercise or multiple exercises with the given project key exist.
     */
    default ProgrammingExercise findOneByProjectKeyOrThrow(String projectKey, boolean withSubmissionPolicy, boolean withBuildConfig) throws EntityNotFoundException {
        List<ProgrammingExercise> exercises;

        if (withSubmissionPolicy && withBuildConfig) {
            exercises = findWithSubmissionPolicyAndBuildConfigByProjectKey(projectKey);
        }
        else if (withSubmissionPolicy) {
            exercises = findWithSubmissionPolicyByProjectKey(projectKey);
        }
        else if (withBuildConfig) {
            exercises = findWithBuildConfigByProjectKey(projectKey);
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
     * Finds a ProgrammingExercise with all data necessary for exercise versioning.
     * Only includes core configuration data, NOT submissions, results, or participation data.
     * <p>
     * The required data spans several independent {@code @OneToMany} collections (testCases, tasks with their
     * test cases, staticCodeAnalysisCategories, auxiliaryRepositories, competencyLinks, categories, gradingCriteria).
     * Fetching the large ones with a single {@code @EntityGraph} produces a Cartesian product: the number of rows the
     * database has to materialize is the product of the collection sizes (e.g. 76 test cases * 62 task-test-case
     * links * 11 SCA categories = 51,832 rows for a single exercise in production), which Hibernate then de-duplicates
     * in memory. That was the dominant application slow query in production.
     * <p>
     * Instead, the large independent collections (testCases, tasks with their test cases, staticCodeAnalysisCategories)
     * are each loaded with their own query and merged into the base exercise in Java. This avoids the Cartesian product
     * entirely and is portable across MySQL and PostgreSQL as it only uses standard JPA {@code @EntityGraph} fetches.
     *
     * @param exerciseId the id of the exercise to be found
     * @return the programming exercise
     */
    default Optional<ProgrammingExercise> findForVersioningById(long exerciseId) {
        // Base query loads the exercise, all to-one associations and the small collections whose mutual product stays
        // small (aux repositories, competency links, categories, grading criteria).
        Optional<ProgrammingExercise> exerciseOptional = findForVersioningBaseById(exerciseId);
        if (exerciseOptional.isEmpty()) {
            return exerciseOptional;
        }
        ProgrammingExercise exercise = exerciseOptional.get();
        // Load each large independent collection with a separate query and merge it into the base exercise. Merging in
        // Java keeps every query free of a Cartesian product between independent collections.
        findForVersioningTestCasesById(exerciseId).ifPresent(fetched -> exercise.setTestCases(fetched.getTestCases()));
        findForVersioningTasksById(exerciseId).ifPresent(fetched -> exercise.setTasks(fetched.getTasks()));
        findForVersioningStaticCodeAnalysisCategoriesById(exerciseId).ifPresent(fetched -> exercise.setStaticCodeAnalysisCategories(fetched.getStaticCodeAnalysisCategories()));
        return Optional.of(exercise);
    }

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "submissionPolicy", "buildConfig", "teamAssignmentConfig",
            "plagiarismDetectionConfig", "auxiliaryRepositories", "competencyLinks", "categories", "gradingCriteria" })
    Optional<ProgrammingExercise> findForVersioningBaseById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "testCases")
    Optional<ProgrammingExercise> findForVersioningTestCasesById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "tasks", "tasks.testCases" })
    Optional<ProgrammingExercise> findForVersioningTasksById(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "staticCodeAnalysisCategories")
    Optional<ProgrammingExercise> findForVersioningStaticCodeAnalysisCategoriesById(long exerciseId);

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
     * Get a programmingExercise with template participation and the latest submission
     *
     * @param exerciseId the id of the exercise that should be fetched.
     * @return the exercise with the given ID, if found.
     */
    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                 LEFT JOIN FETCH pe.templateParticipation tp
                 LEFT JOIN FETCH tp.submissions s
            WHERE pe.id = :exerciseId
            AND (
            s.id = (
                 SELECT MAX(s2.id)
                 FROM Submission s2
                 WHERE s2.participation.id = tp.id
                    )
                 OR s.id IS NULL
                )
            """)
    Optional<ProgrammingExercise> findWithTemplateParticipationAndLatestSubmissionById(@Param("exerciseId") long exerciseId);

    /**
     * Get all programming exercise IDs that need to be scheduled based on their own dates (not individual participation dates).
     * This is the first step in an optimized two-query approach.
     *
     * @param now the current time
     * @return Set of exercise IDs that need scheduling based on exercise-level dates
     */
    @Query("""
            SELECT pe.id
            FROM ProgrammingExercise pe
            WHERE pe.releaseDate > :now
                OR pe.buildAndTestStudentSubmissionsAfterDueDate > :now
                OR pe.dueDate > :now
            """)
    Set<Long> findAllExerciseIdsToBeScheduledByExerciseDates(@Param("now") ZonedDateTime now);

    /**
     * Get all programming exercise IDs that have participations with individual due dates in the future.
     * This is used in combination with findAllExerciseIdsToBeScheduledByExerciseDates for complete scheduling.
     *
     * @param now the current time
     * @return Set of exercise IDs with future individual due dates
     */
    @Query("""
            SELECT DISTINCT p.exercise.id
            FROM StudentParticipation p
            WHERE TYPE(p.exercise) = ProgrammingExercise
                AND p.individualDueDate IS NOT NULL
                AND p.individualDueDate > :now
            """)
    Set<Long> findAllExerciseIdsWithIndividualDueDatesAfter(@Param("now") ZonedDateTime now);

    /**
     * Get programming exercises by IDs without fetching participations.
     * Use this for exercises that don't need participation data for scheduling.
     *
     * @param exerciseIds the exercise IDs
     * @return List of programming exercises
     */
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
            WHERE pe.id IN :exerciseIds
            """)
    List<ProgrammingExercise> findAllByIdIn(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Get all programming exercises that need to be scheduled: Those must satisfy one of the following requirements:
     * <ul>
     * <li>The release date is in the future</li>
     * <li>The build and test student submissions after due date is in the future</li>
     * <li>The due date is in the future</li>
     * <li>There are participations in the exercise with individual due dates in the future</li>
     * </ul>
     * NOTE: This query can be slow on large datasets (7+ seconds observed) because it eagerly fetches all participations.
     * For better performance, consider using the optimized multi-query approach:
     * 1. Call findAllExerciseIdsToBeScheduledByExerciseDates to get exercises by exercise-level dates
     * 2. Call findAllExerciseIdsWithIndividualDueDatesAfter to get exercises with individual due dates
     * 3. Combine the IDs and load exercises using findAllByIdIn
     * 4. Lazy-load participations only when actually needed for scheduling
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
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.studentParticipations pep
                LEFT JOIN FETCH pep.student
                LEFT JOIN FETCH pep.team t
            LEFT JOIN FETCH t.students
            LEFT JOIN FETCH pep.submissions s
            WHERE pe.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(@Param("exerciseId") long exerciseId);

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
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH p.buildConfig
                LEFT JOIN FETCH p.categories
            WHERE p.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigCategories(
            @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT p
            FROM ProgrammingExercise p
                LEFT JOIN FETCH p.testCases tc
                LEFT JOIN FETCH p.staticCodeAnalysisCategories
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH p.buildConfig
                LEFT JOIN FETCH p.gradingCriteria
            WHERE p.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdWithEagerBuildConfigTestCasesStaticCodeAnalysisCategoriesAndTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigAndGradingCriteria(
            @Param("exerciseId") long exerciseId);

    @Query("""
            SELECT p
            FROM ProgrammingExercise p
                LEFT JOIN FETCH p.testCases tc
                LEFT JOIN FETCH p.staticCodeAnalysisCategories
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH p.buildConfig
                LEFT JOIN FETCH p.plagiarismDetectionConfig
                LEFT JOIN FETCH p.gradingCriteria
            WHERE p.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdForImport(@Param("exerciseId") long exerciseId);

    default ProgrammingExercise findByIdForImportElseThrow(long exerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdForImport(exerciseId), exerciseId);
    }

    /**
     * Returns all programming exercises that have a due date after {@code now} and have tests marked with
     * {@link Visibility#AFTER_DUE_DATE} but no buildAndTestStudentSubmissionsAfterDueDate.
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
                AND tc.visibility = de.tum.cit.aet.artemis.assessment.domain.Visibility.AFTER_DUE_DATE
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
     * Returns the programming exercise with its course eagerly fetched, both directly ({@code course}) and via the exam path ({@code exerciseGroup → exam → course}), so that
     * callers can resolve the course via {@code getCourseViaExerciseGroupOrCourseMember} outside a Hibernate session (open-session-in-view is disabled).
     *
     * @param exerciseId the id of the programming exercise to load
     * @return the programming exercise with its course and exam eagerly loaded (empty if the exercise does not exist)
     */
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.course
                LEFT JOIN FETCH pe.exerciseGroup eg
                LEFT JOIN FETCH eg.exam e
                LEFT JOIN FETCH e.course
            WHERE pe.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findWithEagerCourseAndExamById(@Param("exerciseId") long exerciseId);

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
            """)
    long countSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the due date.
     * Should be used for exam dashboard to ignore test run submissions.
     *
     * @param exerciseIds the exercise ids we are interested in
     * @return list of exercises with the count of distinct submissions belonging to the exercise id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                p.exercise.id,
                count(DISTINCT p)
            )
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND p.testRun = FALSE
                AND s.submitted = TRUE
            GROUP BY p.exercise.id
            """)
    List<ExerciseMapEntryDTO> countSubmissionsByExerciseIdsSubmittedIgnoreTestRun(@Param("exerciseIds") Set<Long> exerciseIds);

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
                LEFT JOIN p.submissions s
                LEFT JOIN s.results r
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND r.submission.submitted = TRUE
                AND r.assessor IS NOT NULL
                AND r.completionDate IS NOT NULL
            """)
    long countAssessmentsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM ProgrammingExerciseStudentParticipation p
                LEFT JOIN p.submissions s
                LEFT JOIN s.results r
            WHERE p.exercise.id IN :exerciseIds
                AND p.testRun = FALSE
                AND r.submission.submitted = TRUE
                AND r.assessor IS NOT NULL
                AND r.completionDate IS NOT NULL
            """)
    long countAssessmentsByExerciseIdsSubmittedIgnoreTestRunSubmissions(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the due date.
     *
     * @param exerciseIds the exercise ids to count the submissions for
     * @return the number of the latest submissions belonging to a participation belonging to the exam id, which have the submitted flag set to true and the submission date before
     *         the exercise due date, or no exercise due date at all (only exercises with manual or semi-automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM ProgrammingExerciseStudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                AND p.exercise.id IN :exerciseIds
            """)
    long countSubmissionsByExerciseIdsSubmitted(@Param("exerciseIds") Collection<Long> exerciseIds);

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
            WHERE p.exercise.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                AND p.exercise.id IN :exerciseIds
                AND p.testRun = FALSE
                AND s.submitted = TRUE
            """)
    long countAllSubmissionsByExerciseIdsSubmitted(@Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT COUNT(p)
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :exerciseId
            """)
    long countStudentParticipationsByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT p.id
            FROM ProgrammingExercise p
            WHERE p.exerciseGroup.exam.id = :examId
            """)
    Set<Long> findProgrammingExerciseIdsByExamId(@Param("examId") long examId);

    @EntityGraph(type = LOAD, attributePaths = { "plagiarismDetectionConfig", "teamAssignmentConfig", "buildConfig", "gradingCriteria" })
    Optional<ProgrammingExercise> findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(long exerciseId);

    /**
     * Defines the default entity graph for loading programming exercises along with related configurations.
     * <p>
     * The {@code categories} attribute is included here so that category strings are
     * available immediately without additional lazy loading.
     *
     * @param exerciseId the ID of the programming exercise to fetch
     * @return an {@link Optional} containing the programming exercise with all related configurations if found,
     *         or an empty {@link Optional} otherwise
     */
    @EntityGraph(type = LOAD, attributePaths = { "plagiarismDetectionConfig", "teamAssignmentConfig", "buildConfig", "gradingCriteria", "categories" })
    Optional<ProgrammingExercise> findWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesById(long exerciseId);

    long countByShortNameAndCourse(String shortName, Course course);

    long countByTitleAndCourse(String shortName, Course course);

    long countByShortNameAndExerciseGroupExamCourse(String shortName, Course course);

    long countByTitleAndExerciseGroupExamCourse(String shortName, Course course);

    /**
     * Finds the branch for the given exercise id.
     *
     * @param exerciseId the exercise id to find the branch for
     * @return the branch name, potentially null if no branch is set or if the exercise does not exist
     */
    @Nullable
    @Query("""
            SELECT DISTINCT b.branch
            FROM ProgrammingExerciseBuildConfig b
            WHERE b.programmingExercise.id = :exerciseId
            """)
    String findBranchByExerciseId(@Param("exerciseId") long exerciseId);

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

    @Query("""
            SELECT DISTINCT e
            FROM ProgrammingExercise e
                LEFT JOIN FETCH e.gradingCriteria
                LEFT JOIN FETCH e.exampleSubmissions
            WHERE e.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findByIdWithGradingCriteriaAndExampleSubmissions(@Param("exerciseId") long exerciseId);

    default ProgrammingExercise findByIdWithGradingCriteriaAndExampleSubmissionsElseThrow(long exerciseId) {
        return getValueElseThrow(findByIdWithGradingCriteriaAndExampleSubmissions(exerciseId), exerciseId);
    }

    @Query("""
            SELECT e
            FROM ProgrammingExercise e
                LEFT JOIN FETCH e.competencyLinks
            WHERE e.title = :title
                AND e.course.id = :courseId
            """)
    Optional<ProgrammingExercise> findWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId);

    @Query("""
            SELECT e
            FROM ProgrammingExercise e
                LEFT JOIN FETCH e.competencyLinks
            WHERE e.shortName = :shortName
                AND e.course.id = :courseId
            """)
    Optional<ProgrammingExercise> findByShortNameAndCourseIdWithCompetencies(@Param("shortName") String shortName, @Param("courseId") long courseId);

    default ProgrammingExercise findByIdWithGradingCriteriaElseThrow(long exerciseId) {
        return getValueElseThrow(findByIdWithGradingCriteria(exerciseId), exerciseId);
    }

    /**
     * Find a programming exercise by its id and fetch related plagiarism detection config, team config and grading criteria.
     * Throws an EntityNotFoundException if the exercise cannot be found.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NonNull
    default ProgrammingExercise findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id and fetch related plagiarism detection config,
     * team config, build config, grading criteria, and categories.
     * Throws an EntityNotFoundException if the exercise cannot be found.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NonNull
    default ProgrammingExercise findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        return getValueElseThrow(findWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise with auxiliary repositories by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NonNull
    default ProgrammingExercise findByIdWithAuxiliaryRepositoriesElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithAuxiliaryRepositoriesById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise with auxiliary repositories competencies, and buildConfig by its id and throw an {@link EntityNotFoundException} if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NonNull
    default ProgrammingExercise findForUpdateByIdElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findForUpdateById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise with the submission policy by its id and throw an EntityNotFoundException if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    @NonNull
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
    @NonNull
    // TODO: rename, this method does more than it promises
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id, including its template participation and its build config. Prefer this over
     * calling a template-participation loader and {@link #findBranchByExerciseId} in sequence: both read the same
     * exercise, so one query answers what used to take two.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NonNull
    default ProgrammingExercise findByIdWithTemplateParticipationAndBuildConfigElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithTemplateParticipationAndBuildConfigById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * Find a programming exercise by its id, including auxiliary repositories, template and solution participation and
     * their latest results.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NonNull
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
    @NonNull
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
    @NonNull
    default ProgrammingExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithEagerStudentParticipationsStudentAndSubmissionsById(programmingExerciseId), programmingExerciseId);
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of programming submissions which should be assessed
     *         We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countSubmissionsByExerciseIdSubmitted(long exerciseId) {
        return countSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
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
    @NonNull
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
    @NonNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId);
        return getValueElseThrow(programmingExercise, programmingExerciseId);
    }

    @NonNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        return getValueElseThrow(findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesById(programmingExerciseId), programmingExerciseId);
    }

    @NonNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigById(
                programmingExerciseId);
        return getValueElseThrow(programmingExercise, programmingExerciseId);
    }

    @NonNull
    default ProgrammingExercise findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigElseThrow(
            long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(
                findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigById(programmingExerciseId),
                programmingExerciseId);
    }

    /**
     * Finds a programming exercise by its id with the complete creation graph initialized, see
     * {@link #findForCreationById}, or throws if it does not exist. This is used to return a fully initialized exercise
     * after creation or import, both of which run without an open session and would otherwise expose uninitialized
     * proxies.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NonNull
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
    @NonNull
    default ProgrammingExercise getProgrammingExerciseFromParticipationElseThrow(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise programmingExercise = getProgrammingExerciseFromParticipation(participation);
        if (programmingExercise == null) {
            throw new EntityNotFoundException("No programming exercise found for the participation with id " + participation.getId());
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

        // Programming exercise short names are immutable after creation, so this check only applies to newly created or imported exercises.
        // It guards against student repository URLs exceeding the participation.repository_url column / NAME_MAX limits.
        if (programmingExercise.getShortName().length() > PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH) {
            throw new BadRequestAlertException("The shortname must not exceed " + PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH + " characters", "Exercise",
                    "programmingExerciseShortnameTooLong");
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
     * Find the names of a programming exercise by its id.
     * This method returns a DTO containing the short name of the programming exercise and the short name of the course.
     * We need the left join as otherwise an implicit inner join is performed due to the COALESCE function filtering out
     * programming exercises where not both a course and an exercise group are set, which implies all are filtered out.
     *
     * @param programmingExerciseId the id of the programming exercise
     * @return a DTO containing the short name of the programming exercise and the short name of the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseNamesDTO(
                p.shortName,
                COALESCE(c.shortName, ec.shortName))
            FROM ProgrammingExercise p
              LEFT JOIN p.course c
              LEFT JOIN p.exerciseGroup eg
              LEFT JOIN eg.exam  e
              LEFT JOIN e.course ec
            WHERE p.id = :programmingExerciseId
            """)
    ProgrammingExerciseNamesDTO findNames(@Param("programmingExerciseId") long programmingExerciseId);

    /**
     * Resolve the exercise and owning course metadata for Deimos manual exercise-scope runs.
     * Supports both regular course exercises and exam exercises.
     *
     * @param exerciseId the id of the programming exercise
     * @return projection containing exercise and course metadata
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.deimos.dto.DeimosExerciseScopeInfoDTO(
                p.id,
                p.title,
                COALESCE(c.id, ec.id),
                COALESCE(c.title, ec.title),
                COALESCE(c.courseIcon, ec.courseIcon))
            FROM ProgrammingExercise p
              LEFT JOIN p.course c
              LEFT JOIN p.exerciseGroup eg
              LEFT JOIN eg.exam e
              LEFT JOIN e.course ec
            WHERE p.id = :exerciseId
            """)
    Optional<DeimosExerciseScopeInfoDTO> findDeimosExerciseScopeInfoById(@Param("exerciseId") long exerciseId);

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
        CompetencyLinks(ProgrammingExercise_.COMPETENCY_LINKS),
        Teams(ProgrammingExercise_.TEAMS),
        TutorParticipations(ProgrammingExercise_.TUTOR_PARTICIPATIONS),
        ExampleSubmissions(ProgrammingExercise_.EXAMPLE_SUBMISSIONS),
        Attachments(ProgrammingExercise_.ATTACHMENTS),
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

    /**
     * Find a programming exercise by its id and throw an Exception if it cannot be found
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    default ProgrammingExercise findByIdElseThrow(long programmingExerciseId) {
        return getValueElseThrow(findById(programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, including its test cases, and throw an Exception if it cannot be found.
     *
     * @param exerciseId of the programming exercise.
     * @return The programming exercise with the associated test cases related to the given id.
     * @throws EntityNotFoundException if the programming exercise with the given id cannot be found.
     */
    default ProgrammingExercise findWithTestCasesByIdElseThrow(Long exerciseId) {
        return getArbitraryValueElseThrow(findWithTestCasesById(exerciseId), Long.toString(exerciseId));
    }

    default ProgrammingExercise findWithTemplateParticipationAndLatestSubmissionByIdElseThrow(long exerciseId) {
        return getValueElseThrow(findWithTemplateParticipationAndLatestSubmissionById(exerciseId), exerciseId);
    }

    /**
     * Find a programming exercise by its id, including its build config, and throw an Exception if it cannot be found.
     *
     * @param exerciseId of the programming exercise.
     * @return The programming exercise with the associated build config related to the given id.
     * @throws EntityNotFoundException if the programming exercise with the given id cannot be found.
     */
    default ProgrammingExercise findByIdWithBuildConfigElseThrow(long exerciseId) {
        return getValueElseThrow(findWithBuildConfigById(exerciseId), exerciseId);
    }
}
