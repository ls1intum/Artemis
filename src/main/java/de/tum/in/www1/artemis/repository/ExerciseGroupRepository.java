package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;

/**
 * Spring Data JPA repository for the ExerciseGroup entity.
 */
@Repository
public interface ExerciseGroupRepository extends JpaRepository<ExerciseGroup, Long> {

    @Query("select exerciseGroup from ExerciseGroup exerciseGroup left join fetch exerciseGroup.exam where exerciseGroup.id = :#{#exerciseGroupId}")
    Optional<ExerciseGroup> findWithEagerExamById(@Param("exerciseGroupId") Long exerciseGroupId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    @Query("SELECT e FROM ExerciseGroup e WHERE e.exam.id = :#{#examId}")
    List<ExerciseGroup> findWithEagerExercisesByExamId(@Param("examId") Long examId);
}
