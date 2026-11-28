package de.tum.cit.aet.artemis.assessment.test_repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;

@Lazy
@Repository
@Primary
public interface ExampleSubmissionTestRepository extends ExampleSubmissionRepository {

    Set<ExampleSubmission> findAllByExerciseId(long exerciseId);

    Optional<ExampleSubmission> findBySubmissionId(long submissionId);
}
