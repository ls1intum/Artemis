package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;

/**
 * Spring Data repository for the ProgrammingExerciseTask entity.
 */
@Lazy
@Repository
@Primary
public interface ProgrammingExerciseTaskTestRepository extends ProgrammingExerciseTaskRepository {

    Set<ProgrammingExerciseTask> findByExerciseId(Long exerciseId);
}
