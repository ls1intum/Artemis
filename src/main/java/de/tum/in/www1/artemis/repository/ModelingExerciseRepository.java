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

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ModelingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingExerciseRepository extends JpaRepository<ModelingExercise, Long> {

    @Query("""
            SELECT DISTINCT e FROM ModelingExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<ModelingExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exampleSubmissions", "teamAssignmentConfig", "categories", "exampleSubmissions.submission.results" })
    Optional<ModelingExercise> findWithEagerExampleSubmissionsById(@Param("exerciseId") Long exerciseId);

    @Query("select modelingExercise from ModelingExercise modelingExercise left join fetch modelingExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.results results left join fetch results.feedbacks left join fetch results.assessor left join fetch modelingExercise.teamAssignmentConfig where modelingExercise.id = :#{#exerciseId}")
    Optional<ModelingExercise> findByIdWithExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);

    /**
     * Query which fetches all the modeling exercises for which the user is instructor in the course and matching the search criteria.
     * As JPQL doesn't support unions, the distinction for course exercises and exam exercises is made with sub queries.
     *
     * @param searchTerm search term
     * @param groups     user groups
     * @param pageable   Pageable
     * @return Page with search results
     */
    @Query("""
                SELECT exercise FROM ModelingExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM ModelingExercise courseExercise
                    WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
                    AND (CONCAT(courseExercise.id, '') LIKE %:searchTerm% OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM ModelingExercise examExercise
                    WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (CONCAT(examExercise.id, '') LIKE %:searchTerm% OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<ModelingExercise> queryBySearchTermInCoursesWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT exercise FROM ModelingExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM ModelingExercise courseExercise
                    WHERE (CONCAT(courseExercise.id, '') LIKE %:searchTerm% OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM ModelingExercise examExercise
                    WHERE (CONCAT(examExercise.id, '') LIKE %:searchTerm% OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<ModelingExercise> queryBySearchTermInAllCourses(@Param("searchTerm") String searchTerm, Pageable pageable);

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
            select distinct exercise from ModelingExercise exercise
            where (exercise.assessmentType = 'SEMI_AUTOMATIC' and exercise.dueDate > :#{#now})
            """)
    List<ModelingExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    /**
     * Returns the modeling exercises that are part of an exam with an end date after than the provided date.
     * This method also fetches the exercise group and exam.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ModelingExercise> (can be empty)
     */
    @Query("SELECT me FROM ModelingExercise me LEFT JOIN FETCH me.exerciseGroup eg LEFT JOIN FETCH eg.exam e WHERE e.endDate > :#{#dateTime}")
    List<ModelingExercise> findAllWithEagerExamByExamEndDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<ModelingExercise> findWithStudentParticipationsSubmissionsResultsById(Long exerciseId);

    @NotNull
    default ModelingExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }

    @NotNull
    default ModelingExercise findWithEagerExampleSubmissionsByIdElseThrow(long exerciseId) {
        return findWithEagerExampleSubmissionsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }

    @NotNull
    default ModelingExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findByIdWithExampleSubmissionsAndResults(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }

    @NotNull
    default ModelingExercise findByIdWithStudentParticipationsSubmissionsResultsElseThrow(long exerciseId) {
        return findWithStudentParticipationsSubmissionsResultsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }

    Set<ModelingExercise> findAllByKnowledgeId(Long knowledgeId);
}
