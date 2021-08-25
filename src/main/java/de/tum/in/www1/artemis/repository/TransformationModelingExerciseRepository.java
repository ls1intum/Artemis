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

import de.tum.in.www1.artemis.domain.TransformationModelingExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TransformationModelingExerciseRepository entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TransformationModelingExerciseRepository extends JpaRepository<TransformationModelingExercise, Long> {

    @Query("SELECT e FROM TransformationModelingExercise e WHERE e.course.id = :#{#courseId}")
    List<TransformationModelingExercise> findByCourseId(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exampleSubmissions", "teamAssignmentConfig", "categories", "exampleSubmissions.submission.results" })
    Optional<TransformationModelingExercise> findWithEagerExampleSubmissionsById(@Param("exerciseId") Long exerciseId);

    Page<TransformationModelingExercise> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContainingOrExerciseGroup_Exam_TitleIgnoreCaseContainingOrExerciseGroup_Exam_Course_TitleIgnoreCaseContaining(
            String partialTitle, String partialCourseTitle, String partialExamTitle, String partialExamCourseTitle, Pageable pageable);

    @Query("select modelingExercise from TransformationModelingExercise modelingExercise left join fetch modelingExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.results results left join fetch results.feedbacks left join fetch results.assessor left join fetch modelingExercise.teamAssignmentConfig where modelingExercise.id = :#{#exerciseId}")
    Optional<TransformationModelingExercise> findByIdWithExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);

    /**
     * Query which fetches all the modeling exercises for which the user is instructor in the course and matching the search criteria.
     * As JPQL doesn't support unions, the distinction for course exercises and exam exercises is made with sub queries.
     *
     * @param partialTitle exercise title search term
     * @param partialCourseTitle course title search term
     * @param groups user groups
     * @param pageable Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT me FROM TransformationModelingExercise me
            WHERE (me.id IN
                    (SELECT courseMe.id FROM TransformationModelingExercise courseMe
                    WHERE (courseMe.course.instructorGroupName IN :groups OR courseMe.course.editorGroupName IN :groups)
                    AND (courseMe.title LIKE %:partialTitle% OR courseMe.course.title LIKE %:partialCourseTitle%))
                OR me.id IN
                    (SELECT examMe.id FROM TransformationModelingExercise examMe
                    WHERE (examMe.exerciseGroup.exam.course.instructorGroupName IN :groups OR examMe.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (examMe.title LIKE %:partialTitle% OR examMe.exerciseGroup.exam.course.title LIKE %:partialCourseTitle%)))
                        """)
    Page<TransformationModelingExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Get all modeling exercises that need to be scheduled: Those must satisfy one of the following requirements:
     * <ol>
     * <li>Automatic assessment is enabled and the due date is in the future</li>
     * </ol>
     *
     * @param now the current time
     * @return List of the exercises that should be scheduled
     */
    @Query("""
            select distinct exercise from TransformationModelingExercise exercise
            where (exercise.assessmentType = 'SEMI_AUTOMATIC' and exercise.dueDate > :#{#now})
            """)
    List<TransformationModelingExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TransformationModelingExercise> findWithStudentParticipationsSubmissionsResultsById(Long exerciseId);

    @NotNull
    default TransformationModelingExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Transformation Modeling Exercise", exerciseId));
    }

    @NotNull
    default TransformationModelingExercise findWithEagerExampleSubmissionsByIdElseThrow(long exerciseId) {
        return findWithEagerExampleSubmissionsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Transformation Modeling Exercise", exerciseId));
    }

    @NotNull
    default TransformationModelingExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findByIdWithExampleSubmissionsAndResults(exerciseId).orElseThrow(() -> new EntityNotFoundException("Transformation Modeling Exercise", exerciseId));
    }

    @NotNull
    default TransformationModelingExercise findByIdWithStudentParticipationsSubmissionsResultsElseThrow(long exerciseId) {
        return findWithStudentParticipationsSubmissionsResultsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Transformation Modeling Exercise", exerciseId));
    }
}
