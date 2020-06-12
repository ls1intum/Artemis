package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

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

    @Query("SELECT e FROM ExerciseGroup e WHERE e.exam.id = :#{#examId}")
    List<ExerciseGroup> findByExamId(@Param("examId") Long examId);

    @Query("select exerciseGroup from ExerciseGroup exerciseGroup left join fetch exerciseGroup.exam where exerciseGroup.id = :#{#exerciseGroupId}")
    Optional<ExerciseGroup> findByIdWithEagerExam(@Param("exerciseGroupId") Long exerciseGroupId);
}
