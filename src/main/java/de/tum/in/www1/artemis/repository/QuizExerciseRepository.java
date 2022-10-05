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

    /**
     * Get all quiz exercises by courseId
     *
     * @param courseId the id of the course
     * @return the list of entity
     */
    @Query("""
            SELECT DISTINCT e FROM QuizExercise e
            LEFT JOIN FETCH e.categories
            WHERE e.course.id = :#{#courseId}
            """)
    List<QuizExercise> findAllByCourseIdWithCategories(@Param("courseId") Long courseId);

    /**
     * Get all quiz exercises by examId
     *
     * @param examId the id of the exam
     * @return the list of entity
     */
    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.exerciseGroup.exam.id = :#{#examId}
            """)
    List<QuizExercise> findAllByExamId(Long examId);

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

    @Query("""
                SELECT exercise FROM QuizExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM QuizExercise courseExercise
                    WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
                    AND (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM QuizExercise examExercise
                    WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
                    AND (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<QuizExercise> queryBySearchTermInAllCoursesAndExamsWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT courseExercise FROM QuizExercise courseExercise
            WHERE (courseExercise.course.instructorGroupName IN :groups OR courseExercise.course.editorGroupName IN :groups)
            AND (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%)
                """)
    Page<QuizExercise> queryBySearchTermInAllCoursesWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT examExercise FROM QuizExercise examExercise
            WHERE (examExercise.exerciseGroup.exam.course.instructorGroupName IN :groups OR examExercise.exerciseGroup.exam.course.editorGroupName IN :groups)
            AND (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)
                """)
    Page<QuizExercise> queryBySearchTermInAllExamsWhereEditorOrInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    @Query("""
            SELECT exercise FROM QuizExercise exercise
            WHERE (exercise.id IN
                    (SELECT courseExercise.id FROM QuizExercise courseExercise
                    WHERE (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%))
                OR exercise.id IN
                    (SELECT examExercise.id FROM QuizExercise examExercise
                    WHERE (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)))
                        """)
    Page<QuizExercise> queryBySearchTermInAllCoursesAndExams(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT courseExercise FROM QuizExercise courseExercise
            WHERE (CONCAT(courseExercise.id, '') = :#{#searchTerm} OR courseExercise.title LIKE %:searchTerm% OR courseExercise.course.title LIKE %:searchTerm%)
                """)
    Page<QuizExercise> queryBySearchTermInAllCourses(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT examExercise FROM QuizExercise examExercise
            WHERE (CONCAT(examExercise.id, '') = :#{#searchTerm} OR examExercise.title LIKE %:searchTerm% OR examExercise.exerciseGroup.exam.course.title LIKE %:searchTerm%)
                """)
    Page<QuizExercise> queryBySearchTermInAllExams(@Param("searchTerm") String searchTerm, Pageable pageable);

}
