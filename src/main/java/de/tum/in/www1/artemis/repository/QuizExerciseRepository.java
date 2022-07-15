package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    @Query("""
            SELECT DISTINCT e FROM QuizExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<QuizExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.exerciseGroup.exam.id = :#{#examId}
            """)
    List<QuizExercise> findByExamId(Long examId);

    @Query("""
            SELECT DISTINCT qe
            FROM QuizExercise qe
            LEFT JOIN qe.quizBatches b
            WHERE b.startTime > :#{#earliestReleaseDate}
            """)
    List<QuizExercise> findAllPlannedToStartAfter(ZonedDateTime earliestReleaseDate);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories", "quizBatches" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions" })
    Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizBatches" })
    Optional<QuizExercise> findWithEagerBatchesById(Long quizExerciseId);

    @NotNull
    default QuizExercise findByIdElseThrow(Long quizExerciseId) throws EntityNotFoundException {
        return findById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get one quiz exercise
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @Nullable
    default QuizExercise findOne(Long quizExerciseId) {
        return findById(quizExerciseId).orElse(null);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default QuizExercise findByIdWithQuestionsElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get one quiz exercise by id and eagerly load batches
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default QuizExercise findByIdWithBatchesElseThrow(Long quizExerciseId) {
        return findWithEagerBatchesById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get one quiz exercise by id and eagerly load questions and statistics
     *
     * @param quizExerciseId the id of the entity
     * @return the quiz exercise entity
     */
    @Nullable
    default QuizExercise findOneWithQuestionsAndStatistics(Long quizExerciseId) {
        return findWithEagerQuestionsAndStatisticsById(quizExerciseId).orElse(null);
    }

    @NotNull
    default QuizExercise findByIdWithQuestionsAndStatisticsElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsAndStatisticsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    default List<QuizExercise> findAllPlannedToStartInTheFuture() {
        return findAllPlannedToStartAfter(ZonedDateTime.now());
    }

    /**
     * Query which fetches all the quiz exercises which match the search criteria.
     *
     * @param partialTitle exercise title search term
     * @param partialCourseTitle course title search term
     * @param partialExamTitle exam title search term
     * @param partialExamCourseTitle exam course title search term
     * @param pageable Pageable
     * @return Page with search results
     */
    Page<QuizExercise> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContainingOrExerciseGroup_Exam_TitleIgnoreCaseContainingOrExerciseGroup_Exam_Course_TitleIgnoreCaseContaining(
            String partialTitle, String partialCourseTitle, String partialExamTitle, String partialExamCourseTitle, Pageable pageable);

    /**
     * Query which fetches all the quiz exercises for which the user is instructor in the course and matching the search criteria.
     * As JPQL doesn't support unions, the distinction for course exercises and exam exercises is made with sub queries.
     *
     * @param partialTitle exercise title search term
     * @param partialCourseTitle course title search term
     * @param groups user groups
     * @param pageable Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT qe FROM QuizExercise qe
            WHERE (qe.id IN
                    (SELECT courseQe.id FROM QuizExercise courseQe
                    WHERE (courseQe.course.instructorGroupName IN :groups OR courseQe.course.editorGroupName IN :groups)
                    AND (courseQe.title LIKE %:partialTitle% OR courseQe.course.title LIKE %:partialCourseTitle%))
                OR qe.id IN
                    (SELECT examQe.id FROM QuizExercise examQe
                    WHERE (examQe.exerciseGroup.exam.course.instructorGroupName IN :groups OR examQe.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (examQe.title LIKE %:partialTitle% OR examQe.exerciseGroup.exam.course.title LIKE %:partialCourseTitle%)))
                        """)
    Page<QuizExercise> findByTitleInExerciseOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);
}
