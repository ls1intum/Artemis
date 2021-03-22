package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ExerciseGroup entity.
 */
@Repository
public interface ExerciseGroupRepository extends JpaRepository<ExerciseGroup, Long> {

    List<ExerciseGroup> findByExamId(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    @Query("SELECT e FROM ExerciseGroup e WHERE e.id = :#{#exerciseGroupId}")
    Optional<ExerciseGroup> findWithExercisesById(@Param("exerciseGroupId") Long exerciseGroupId);

    @EntityGraph(type = LOAD, attributePaths = { "exam", "exercises" })
    @Query("SELECT e FROM ExerciseGroup e WHERE e.exam.id = :#{#examId}")
    List<ExerciseGroup> findWithExamAndExercisesByExamId(@Param("examId") Long examId);

    /**
     * Get one exerciseGroup by id with the corresponding exam.
     *
     * @param exerciseGroupId the id of the entity
     * @return the entity
     */
    @NotNull
    default ExerciseGroup findByIdElseThrow(long exerciseGroupId) {
        // Note: exam is loaded eagerly anyway
        return findById(exerciseGroupId).orElseThrow(() -> new EntityNotFoundException("ExerciseGroup", exerciseGroupId));
    }

    /**
     * Retrieve the course through ExerciseGroup -> Exam -> Course
     *
     * @param exerciseGroupId the id of the exerciseGroup for which the course is retrieved
     * @return the Course of the Exercise
     */
    default Course retrieveCourseOverExerciseGroup(long exerciseGroupId) {
        ExerciseGroup exerciseGroup = findByIdElseThrow(exerciseGroupId);
        return exerciseGroup.getExam().getCourse();
    }

    /**
     * Get one exerciseGroup by id with all exercises.
     *
     * @param exerciseGroupId the id of the entity
     * @return the exercise group with all exercise
     */
    @NotNull
    default ExerciseGroup findByIdWithExercisesElseThrow(long exerciseGroupId) {
        return findWithExercisesById(exerciseGroupId).orElseThrow(() -> new EntityNotFoundException("ExerciseGroup", exerciseGroupId));
    }
}
