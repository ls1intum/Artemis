package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

    // Does a max join on the result table for each participation by result id (the newer the result id, the newer the result). This makes sure that we only receive the latest
    // result for the template and the solution participation if they exist.
    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.templateParticipation tp LEFT JOIN FETCH pe.solutionParticipation sp LEFT JOIN FETCH tp.results as tpr LEFT JOIN FETCH sp.results AS spr WHERE pe.course.id = :#{#courseId} AND (tpr.id = (SELECT MAX(id) FROM tp.results) OR tpr.id = NULL) AND (spr.id = (SELECT MAX(id) FROM sp.results) OR spr.id = NULL)")
    List<ProgrammingExercise> findAllWithLatestResultForTemplateSolutionParticipationsByCourseId(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "teamAssignmentConfig", "categories" })
    Optional<ProgrammingExercise> findWithTemplateParticipationAndSolutionParticipationById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "testCases")
    Optional<ProgrammingExercise> findWithTestCasesById(Long exerciseId);

    // Get an a programmingExercise with template and solution participation, each with the latest result and feedbacks.
    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.templateParticipation tp LEFT JOIN FETCH pe.solutionParticipation sp "
            + "LEFT JOIN FETCH tp.results AS tpr LEFT JOIN FETCH sp.results as spr LEFT JOIN FETCH tpr.feedbacks LEFT JOIN FETCH spr.feedbacks "
            + "WHERE pe.id = :#{#exerciseId} AND (tpr.id = (SELECT MAX(id) FROM tp.results) OR tpr.id = NULL) "
            + "AND (spr.id = (SELECT MAX(id) FROM sp.results) OR spr.id = NULL)")
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationById(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT pe from ProgrammingExercise AS pe LEFT JOIN FETCH pe.studentParticipations")
    List<ProgrammingExercise> findWithEagerParticipationsAll();

    @Query("SELECT DISTINCT pe FROM ProgrammingExercise AS pe LEFT JOIN FETCH pe.studentParticipations pep LEFT JOIN FETCH pep.submissions")
    List<ProgrammingExercise> findAllWithEagerParticipationsAndSubmissions();

    @Query("SELECT DISTINCT pe from ProgrammingExercise AS pe LEFT JOIN FETCH pe.templateParticipation LEFT JOIN FETCH pe.solutionParticipation")
    List<ProgrammingExercise> findAllWithEagerTemplateAndSolutionParticipations();

    @EntityGraph(type = LOAD, attributePaths = "studentParticipations")
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsById(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<ProgrammingExercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);

    ProgrammingExercise findOneByTemplateParticipationId(Long templateParticipationId);

    ProgrammingExercise findOneBySolutionParticipationId(Long solutionParticipationId);

    @Query("SELECT pe FROM ProgrammingExercise pe WHERE pe.course.instructorGroupName IN :groups AND pe.shortName IS NOT NULL AND (pe.title LIKE %:partialTitle% OR pe.course.title LIKE %:partialCourseTitle%)")
    Page<ProgrammingExercise> findAllByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("groups") Set<String> groups, Pageable pageable);

    Page<ProgrammingExercise> findAllByTitleIgnoreCaseContainingAndShortNameNotNullOrCourseTitleIgnoreCaseContainingAndShortNameNotNull(String partialTitle,
            String partialCourseTitle, Pageable pageable);

    @Query("SELECT p FROM ProgrammingExercise p LEFT JOIN FETCH p.testCases LEFT JOIN FETCH p.exerciseHints LEFT JOIN FETCH p.templateParticipation LEFT JOIN FETCH p.solutionParticipation WHERE p.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findWithEagerTestCasesHintsAndTemplateAndSolutionParticipationsById(@Param("exerciseId") Long exerciseId);

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
     * We therefore have to check here that a submission exists, that was submitted before the deadline.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of distinct submissions belonging to the exercise id
     */
    @Query("SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p WHERE p.exercise.id = :#{#exerciseId} AND EXISTS (SELECT s FROM ProgrammingSubmission s WHERE s.participation.id = p.id AND s.submitted = TRUE)")
    long countSubmissionsByExerciseIdSubmitted(@Param("exerciseId") Long exerciseId);

    /**
     * In distinction to other exercise types, students can have multiple submissions in a programming exercise.
     * We therefore have to check here if any submission of the student was submitted before the deadline.
     *
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all (only exercises with manual or semi automatic correction are considered)
     */
    @Query("SELECT COUNT (DISTINCT p) FROM ProgrammingExerciseStudentParticipation p WHERE p.exercise.assessmentType <> 'AUTOMATIC' AND p.exercise.course.id = :#{#courseId} AND EXISTS (SELECT s FROM ProgrammingSubmission s WHERE s.participation.id = p.id AND s.submitted = TRUE)")
    long countSubmissionsByCourseIdSubmitted(@Param("courseId") Long courseId);

    List<ProgrammingExercise> findAllByCourseInstructorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourseTeachingAssistantGroupNameIn(Set<String> groupNames);

    @Query("SELECT pe FROM ProgrammingExercise pe WHERE pe.course.instructorGroupName IN :#{#groupNames} OR pe.course.teachingAssistantGroupName IN :#{#groupNames}")
    List<ProgrammingExercise> findAllByInstructorOrTAGroupNameIn(@Param("groupNames") Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse(Course course);

    List<ProgrammingExercise> findAllByShortNameAndCourse(String shortName, Course course);
}
