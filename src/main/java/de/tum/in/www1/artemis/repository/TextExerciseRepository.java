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

    @Query("""
                SELECT exercise FROM TextExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM TextExercise courseExercise
                    WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
                    AND (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM TextExercise examExercise
                    WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<TextExercise> queryBySearchTermInAllCoursesAndExamsWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT courseExercise FROM TextExercise courseExercise
            WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
            AND (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%)
                """)
    Page<TextExercise> queryBySearchTermInAllCoursesWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT examExercise FROM TextExercise examExercise
            WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
            AND (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)
                """)
    Page<TextExercise> queryBySearchTermInAllExamsWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT exercise FROM TextExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM TextExercise courseExercise
                    WHERE (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM TextExercise examExercise
                    WHERE (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<TextExercise> queryBySearchTermInAllCoursesAndExams(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT courseExercise FROM TextExercise courseExercise
            WHERE (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%)
                """)
    Page<TextExercise> queryBySearchTermInAllCourses(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT examExercise FROM TextExercise examExercise
            WHERE (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)
                """)
    Page<TextExercise> queryBySearchTermInAllExams(@Param("searchTerm") String searchTerm, Pageable pageable);

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
