package de.tum.cit.aet.artemis.exam.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("""
            SELECT eg
            FROM Exam exam
                LEFT JOIN exam.exerciseGroups eg
                LEFT JOIN FETCH eg.exercises
                LEFT JOIN FETCH eg.exam
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
    @NotNull
    default ExerciseGroup findByIdWithExercisesElseThrow(long exerciseGroupId) {
        return getValueElseThrow(findWithExercisesById(exerciseGroupId), exerciseGroupId);
    }
}
