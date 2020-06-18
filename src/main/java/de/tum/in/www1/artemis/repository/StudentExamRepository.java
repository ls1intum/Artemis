package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.StudentExam;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

/**
 * Spring Data JPA repository for the StudentExam entity.
 */
@Repository
public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {

    List<StudentExam> findByExamId(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithEagerExercisesById(Long id);
}
