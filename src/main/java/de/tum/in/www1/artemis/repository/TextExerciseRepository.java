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

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TextExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextExerciseRepository extends JpaRepository<TextExercise, Long> {

    @Query("""
            SELECT DISTINCT e FROM TextExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<TextExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "teamAssignmentConfig", "categories" })
    Optional<TextExercise> findWithEagerTeamAssignmentConfigAndCategoriesById(Long exerciseId);

    List<TextExercise> findByAssessmentTypeAndDueDateIsAfter(AssessmentType assessmentType, ZonedDateTime dueDate);

    /**
     * Query which fetches all the text exercises for which the user is instructor in the course and matching the search criteria.
     * As JPQL doesn't support unions, the distinction for course exercises and exam exercises is made with sub queries.
     *
     * @param partialTitle exercise title search term
     * @param partialCourseTitle course title search term
     * @param groups user groups
     * @param pageable Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT te FROM TextExercise te
            WHERE (te.id IN
                    (SELECT courseTe.id FROM TextExercise courseTe
                    WHERE (courseTe.course.instructorGroupName IN :groups OR courseTe.course.editorGroupName IN :groups)
                    AND (courseTe.title LIKE %:partialTitle% OR courseTe.course.title LIKE %:partialCourseTitle%))
                OR te.id IN
                    (SELECT examTe.id FROM TextExercise examTe
                    WHERE (examTe.exerciseGroup.exam.course.instructorGroupName IN :groups OR examTe.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (examTe.title LIKE %:partialTitle% OR examTe.exerciseGroup.exam.course.title LIKE %:partialCourseTitle%)))
                        """)
    Page<TextExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    Page<TextExercise> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContainingOrExerciseGroup_Exam_TitleIgnoreCaseContainingOrExerciseGroup_Exam_Course_TitleIgnoreCaseContaining(
            String partialTitle, String partialCourseTitle, String partialExamTitle, String partialExamCourseTitle, Pageable pageable);

    @Query("select textExercise from TextExercise textExercise left join fetch textExercise.exampleSubmissions exampleSubmissions left join fetch exampleSubmissions.submission submission left join fetch submission.results result left join fetch result.feedbacks left join fetch submission.blocks left join fetch result.assessor left join fetch textExercise.teamAssignmentConfig where textExercise.id = :#{#exerciseId}")
    Optional<TextExercise> findByIdWithExampleSubmissionsAndResults(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.submissions", "studentParticipations.submissions.results" })
    Optional<TextExercise> findWithStudentParticipationsAndSubmissionsById(Long exerciseId);

    @NotNull
    default TextExercise findByIdElseThrow(long exerciseId) {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @NotNull
    default TextExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return findByIdWithExampleSubmissionsAndResults(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    @NotNull
    default TextExercise findByIdWithStudentParticipationsAndSubmissionsElseThrow(long exerciseId) {
        return findWithStudentParticipationsAndSubmissionsById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Text Exercise", exerciseId));
    }

    /**
     * Find all exercises with *Due Date* in the future.
     *
     * @return List of Text Exercises
     */
    default List<TextExercise> findAllAutomaticAssessmentTextExercisesWithFutureDueDate() {
        return findByAssessmentTypeAndDueDateIsAfter(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now());
    }

    Set<TextExercise> findAllByKnowledgeId(Long knowledgeId);
}
