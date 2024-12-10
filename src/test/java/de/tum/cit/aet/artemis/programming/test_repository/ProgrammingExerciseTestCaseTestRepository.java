package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

@Repository
@Primary
public interface ProgrammingExerciseTestCaseTestRepository extends ProgrammingExerciseTestCaseRepository {

    Optional<ProgrammingExerciseTestCase> findByExerciseIdAndTestName(long exerciseId, String testName);
}
