package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

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

    @Query("SELECT e FROM ModelingExercise e WHERE e.course.id = :#{#courseId}")
    List<ModelingExercise> findByCourseId(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exampleSubmissions", "teamAssignmentConfig", "categories", "exampleSubmissions.submission.results" })
    Optional<ModelingExercise> findWithEagerExampleSubmissionsById(@Param("exerciseId") Long exerciseId);

    Page<ModelingExercise> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContainingOrExerciseGroup_Exam_TitleIgnoreCaseContainingOrExerciseGroup_Exam_Course_TitleIgnoreCaseContaining(
            String partialTitle, String partialCourseTitle, String partialExamTitle, String partialExamCourseTitle, Pageable pageable);

    @Query("select modelingExercise from ModelingExercise modelingExercise left join fetch modelingExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.results results left join fetch results.feedbacks left join fetch results.assessor left join fetch modelingExercise.teamAssignmentConfig where modelingExercise.id = :#{#exerciseId}")
    Optional<ModelingExercise> findByIdWithExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);

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
    @Query("select me from ModelingExercise me where (me.id in (select courseMe.id from ModelingExercise courseMe where courseMe.course.instructorGroupName in :groups and (courseMe.title like %:partialTitle% or courseMe.course.title like %:partialCourseTitle%)) or me.id in (select examMe.id from ModelingExercise examMe where examMe.exerciseGroup.exam.course.instructorGroupName in :groups and (examMe.title like %:partialTitle% or examMe.exerciseGroup.exam.course.title like %:partialCourseTitle%)))")
    Page<ModelingExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<ModelingExercise> findWithStudentParticipationsSubmissionsResultsById(Long exerciseId);

    @NotNull
    default ModelingExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }

    @NotNull
    default ModelingExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findByIdWithExampleSubmissionsAndResults(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }

    @NotNull
    default ModelingExercise findByIdWithStudentParticipationsSubmissionsResultsElseThrow(long exerciseId) {
        return findWithStudentParticipationsSubmissionsResultsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Modeling Exercise", exerciseId));
    }
}
