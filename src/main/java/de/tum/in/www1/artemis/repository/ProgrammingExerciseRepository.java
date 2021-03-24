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
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

    // Does a max join on the result table for each participation by result id (the newer the result id, the newer the result). This makes sure that we only receive the latest
    // result for the template and the solution participation if they exist.
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp left join fetch tp.results as tpr left join fetch sp.results as spr where pe.course.id = :#{#courseId} and (tpr.id = (select max(id) from tp.results) or tpr.id = null) and (spr.id = (select max(id) from sp.results) or spr.id = null)")
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

    // Get an a programmingExercise with template and solution participation, each with the latest result and feedbacks.
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp "
            + "left join fetch tp.results as tpr left join fetch sp.results as spr left join fetch tpr.feedbacks left join fetch spr.feedbacks "
            + "left join fetch tpr.submission left join fetch spr.submission " + "where pe.id = :#{#exerciseId} and (tpr.id = (select max(id) from tp.results) or tpr.id = null) "
            + "and (spr.id = (select max(id) from sp.results) or spr.id = null)")
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationLatestResultById(@Param("exerciseId") Long exerciseId);

    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.studentParticipations")
    List<ProgrammingExercise> findAllWithEagerParticipations();

    @Query("select distinct pe from ProgrammingExercise pe where pe.course.endDate between :#{#endDate1} and :#{#endDate2}")
    List<ProgrammingExercise> findAllByRecentCourseEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.studentParticipations where pe.dueDate between :#{#endDate1} and :#{#endDate2} or pe.exerciseGroup.exam.endDate between :#{#endDate1} and :#{#endDate2}")
    List<ProgrammingExercise> findAllWithStudentParticipationByRecentEndDate(@Param("endDate1") ZonedDateTime endDate1, @Param("endDate2") ZonedDateTime endDate2);

    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.studentParticipations pep left join fetch pep.submissions")
    List<ProgrammingExercise> findAllWithEagerParticipationsAndSubmissions();

    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation left join fetch pe.solutionParticipation")
    List<ProgrammingExercise> findAllWithEagerTemplateAndSolutionParticipations();

    @EntityGraph(type = LOAD, attributePaths = "studentParticipations")
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "studentParticipations" })
    Optional<ProgrammingExercise> findWithAllParticipationsById(Long exerciseId);

    ProgrammingExercise findOneByTemplateParticipationId(Long templateParticipationId);

    ProgrammingExercise findOneBySolutionParticipationId(Long solutionParticipationId);

    /**
     * Query which fetches all the programming exercises for which the user is instructor in the course and matching the search criteria.
     * As JPQL doesn't support unions, the distinction for course exercises and exam exercises is made with sub queries.
     *
     * @param partialTitle exercise title search term
     * @param partialCourseTitle course title search term
     * @param groups user groups
     * @param pageable Pageable
     * @return Page with search results
     */
    @Query("select pe from ProgrammingExercise pe where pe.shortName is not null and (pe.id in (select coursePe.id from ProgrammingExercise coursePe where coursePe.course.instructorGroupName in :groups and (coursePe.title like %:partialTitle% or coursePe.course.title like %:partialCourseTitle%)) or pe.id in (select examPe.id from ProgrammingExercise examPe where examPe.exerciseGroup.exam.course.instructorGroupName in :groups and (examPe.title like %:partialTitle% or examPe.exerciseGroup.exam.course.title like %:partialCourseTitle%)))")
    Page<ProgrammingExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("groups") Set<String> groups, Pageable pageable);

    Page<ProgrammingExercise> findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(String partialTitle,
            String partialCourseTitle, Pageable pageable);

    @Query("select p from ProgrammingExercise p left join fetch p.testCases left join fetch p.staticCodeAnalysisCategories left join fetch p.exerciseHints left join fetch p.templateParticipation left join fetch p.solutionParticipation where p.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipations(@Param("exerciseId") Long exerciseId);

    /**
     * Returns the programming exercises that have a buildAndTestStudentSubmissionsAfterDueDate higher than the provided date.
     * This can't be done as a standard query as the property contains the word 'And'.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("select pe from ProgrammingExercise pe where pe.buildAndTestStudentSubmissionsAfterDueDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * Returns the programming exercises that have manual assessment enabled and a due date higher than the provided date.
     *
     * @param dateTime ZonedDateTime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("select pe from ProgrammingExercise pe where pe.assessmentType <> 'AUTOMATIC' and pe.dueDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllByManualAssessmentAndDueDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * Returns the programming exercises that are part of an exam with an end date after than the provided date.
     * This method also fetches the exercise group and exam.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("select pe from ProgrammingExercise pe left join fetch pe.exerciseGroup eg left join fetch eg.exam e where e.endDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllWithEagerExamByExamEndDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id
     */
    @Query("SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p WHERE p.exercise.id = :#{#exerciseId} AND EXISTS (SELECT s FROM ProgrammingSubmission s WHERE s.participation.id = p.id AND s.submitted = TRUE)")
    long countSubmissionsByExerciseIdSubmitted(@Param("exerciseId") Long exerciseId);

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
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = FALSE
            AND EXISTS (SELECT s FROM ProgrammingSubmission s
                WHERE s.participation.id = p.id
                AND s.submitted = TRUE)
            """)
    long countSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(@Param("exerciseId") Long exerciseId);

    /** needs improvement
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id that are assessed
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p left join p.results r
            WHERE p.exercise.id = :#{#exerciseId}
            AND r.submission.submitted = TRUE
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
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = FALSE
            AND EXISTS (SELECT s FROM ProgrammingSubmission s
                WHERE s.participation.id = p.id
                AND s.submitted = TRUE
                AND EXISTS (SELECT r.assessor FROM s.results r
                        WHERE r.assessor IS NOT NULL
                        AND r.completionDate IS NOT NULL))
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
                WHERE p.exercise.assessmentType <> 'AUTOMATIC'
                AND p.exercise.exerciseGroup.exam.id = :#{#examId}
                AND p.submissions IS NOT EMPTY
            """)
    long countSubmissionsByExamIdSubmitted(@Param("examId") Long examId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all (only exercises with manual or semi automatic correction are considered)
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p join p.submissions s
                WHERE p.exercise.assessmentType <> 'AUTOMATIC'
                AND p.exercise.course.id = :#{#courseId}
                AND s.submitted = TRUE
            """)
    long countSubmissionsByCourseIdSubmitted(@Param("courseId") Long courseId);

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
    default ProgrammingExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = findWithEagerStudentParticipationsStudentAndSubmissionsById(programmingExerciseId);
        return programmingExercise.orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
    }

    /**
     * @param exerciseId     the exercise we are interested in
     * @param ignoreTestRuns should be set for exam exercises
     * @return the number of programming submissions which should be assessed
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    default long countSubmissionsByExerciseIdSubmitted(Long exerciseId, boolean ignoreTestRuns) {
        if (ignoreTestRuns) {
            return countSubmissionsByExerciseIdSubmittedIgnoreTestRunSubmissions(exerciseId);
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
