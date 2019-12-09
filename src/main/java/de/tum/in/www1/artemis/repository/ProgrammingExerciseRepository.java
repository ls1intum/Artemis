package de.tum.in.www1.artemis.repository;

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

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

    // Does a max join on the result table for each participation by result id (the newer the result id, the newer the result). This makes sure that we only receive the latest
    // result for the template and the solution participation if they exist.
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp left join fetch tp.results as tpr left join fetch sp.results as spr where pe.course.id = :#{#courseId} and (tpr.id = (select max(id) from tp.results) or tpr.id = null) and (spr.id = (select max(id) from sp.results) or spr.id = null)")
    List<ProgrammingExercise> findByCourseIdWithLatestResultForTemplateSolutionParticipations(@Param("courseId") Long courseId);

    // Override to automatically fetch the templateParticipation and solutionParticipation
    @Override
    @NotNull
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation left join fetch pe.solutionParticipation where pe.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findById(@Param("exerciseId") Long exerciseId);

    @EntityGraph(attributePaths = "testCases")
    @Query("select distinct pe from ProgrammingExercise pe where pe.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findByIdWithTestCases(@Param("exerciseId") Long id);

    // Get an a programmingExercise with template and solution participation, each with the latest result and feedbacks.
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp "
            + "left join fetch tp.results as tpr left join fetch sp.results as spr left join fetch tpr.feedbacks left join fetch spr.feedbacks "
            + "where pe.id = :#{#exerciseId} and (tpr.id = (select max(id) from tp.results) or tpr.id = null) "
            + "and (spr.id = (select max(id) from sp.results) or spr.id = null)")
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationById(@Param("exerciseId") Long exerciseId);

    @Query("select distinct pe from ProgrammingExercise as pe left join fetch pe.studentParticipations")
    List<ProgrammingExercise> findAllWithEagerParticipations();

    @EntityGraph(attributePaths = "studentParticipations")
    @Query("select distinct pe from ProgrammingExercise pe where pe.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findByIdWithEagerParticipations(@Param("exerciseId") Long exerciseId);

    @EntityGraph(attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    @Query("select distinct pe from ProgrammingExercise pe where pe.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findByIdWithEagerParticipationsAndSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("select distinct pe from ProgrammingExercise as pe left join fetch pe.studentParticipations pep left join fetch pep.submissions")
    List<ProgrammingExercise> findAllWithEagerParticipationsAndSubmissions();

    ProgrammingExercise findOneByTemplateParticipationId(Long templateParticipationId);

    ProgrammingExercise findOneBySolutionParticipationId(Long solutionParticipationId);

    @EntityGraph(attributePaths = "course")
    @Query("select pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp where tp.id = :#{#participationId} or sp.id = :#{#participationId}")
    Optional<ProgrammingExercise> findOneByTemplateParticipationIdOrSolutionParticipationId(@Param("participationId") Long participationId);

    @Query("select pe from ProgrammingExercise pe where pe.course.instructorGroupName in :groups and pe.shortName is not null and (pe.title like %:partialTitle% or pe.course.title like %:partialCourseTitle%)")
    Page<ProgrammingExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(String partialTitle, String partialCourseTitle, Set<String> groups, Pageable pageable);

    Page<ProgrammingExercise> findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(String partialTitle,
            String partialCourseTitle, Pageable pageable);

    @Query("select p from ProgrammingExercise p left join fetch p.testCases left join fetch p.exerciseHints left join fetch p.templateParticipation left join fetch p.solutionParticipation where p.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findByIdWithEagerTestCasesHintsAndTemplateAndSolutionParticipations(Long exerciseId);

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
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * Also submissions can be created after the due date (e.g. by instructor builds)
     * We therefore have to check for all distinct participations that have at least one submission.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id
     */
    @Query("SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p left join p.submissions WHERE p.exercise.id = :#{#exerciseId}")
    long countSubmissions(@Param("exerciseId") Long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("SELECT COUNT (DISTINCT participation) FROM ProgrammingExerciseStudentParticipation participation WHERE participation.exercise.course.id = :#{#courseId} AND (participation.exercise.dueDate IS NULL OR EXISTS (SELECT submission FROM ProgrammingSubmission submission WHERE submission.participation.id = participation.id AND submission.submissionDate IS NOT NULL AND submission.submitted = TRUE AND submission.submissionDate < participation.exercise.dueDate))")
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") Long courseId);
}
