package de.tum.cit.aet.artemis.exam.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Spring Data JPA repository for the ExerciseGroup entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExerciseGroupRepository extends ArtemisJpaRepository<ExerciseGroup, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<ExerciseGroup> findWithExercisesById(Long exerciseGroupId);

    /**
     * Finds the exercise groups of an exam, in their stored order, with their exercises.
     * <p>
     * The course is fetch-joined even though no caller reads it: {@code Exam.course} is a {@code @ManyToOne} without an
     * explicit fetch type, so it is EAGER, and hydrating the fetched exam would otherwise cost a second round trip for a
     * course that is immediately discarded (the list DTO sets the exam to null).
     *
     * @param examId the exam whose exercise groups are returned
     * @return the exercise groups of the exam, ordered as stored, or an empty list if it has none
     */
    @Query("""
            SELECT eg
            FROM Exam exam
                JOIN exam.exerciseGroups eg
                LEFT JOIN FETCH eg.exercises
                LEFT JOIN FETCH eg.exam eagerExam
                LEFT JOIN FETCH eagerExam.course
            WHERE exam.id = :examId
            ORDER BY INDEX(eg)
            """)
    // INDEX() is used to retrieve the order saved by @OrderColumn, see https://en.wikibooks.org/wiki/Java_Persistence/JPQL#Special_Operators
    List<ExerciseGroup> findWithExamAndExercisesByExamId(@Param("examId") Long examId);

    /**
     * Get one exerciseGroup by id with all exercises.
     *
     * @param exerciseGroupId the id of the entity
     * @return the exercise group with all exercise
     */
    @NonNull
    default ExerciseGroup findByIdWithExercisesElseThrow(long exerciseGroupId) {
        return getValueElseThrow(findWithExercisesById(exerciseGroupId), exerciseGroupId);
    }

    // Spring Data only allows void, int/Integer or long/Long here; moveToExerciseGroupIfNoStudentExams wraps the count.
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Exercise e
            SET e.exerciseGroup = (
                    SELECT eg
                    FROM ExerciseGroup eg
                    WHERE eg.id = :exerciseGroupId
                )
            WHERE e.id = :exerciseId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM StudentExam se
                      WHERE se.exam.id = :examId
                  )
            """)
    int updateExerciseGroupIfNoStudentExams(@Param("exerciseId") long exerciseId, @Param("exerciseGroupId") long exerciseGroupId, @Param("examId") long examId);

    /**
     * Moves an exam exercise into a different exercise group, but only while the exam has no student exam at all.
     * The guard sits inside the statement so a concurrent generation cannot commit between check and write. Being a
     * bulk update, it bypasses the persistence context: a previously loaded {@code Exercise} keeps its old group.
     *
     * @param exerciseId      the id of the exercise to move
     * @param exerciseGroupId the id of the target exercise group
     * @param examId          the id of the exam the exercise belongs to
     * @return whether the exercise was moved; false when a student exam already exists for the exam
     */
    default boolean moveToExerciseGroupIfNoStudentExams(long exerciseId, long exerciseGroupId, long examId) {
        return updateExerciseGroupIfNoStudentExams(exerciseId, exerciseGroupId, examId) > 0;
    }
}
