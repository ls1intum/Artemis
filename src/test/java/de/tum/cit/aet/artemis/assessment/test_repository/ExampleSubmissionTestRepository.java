package de.tum.cit.aet.artemis.assessment.test_repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;

@Repository
public interface ExampleSubmissionTestRepository extends ExampleSubmissionRepository {

    Optional<ExampleSubmission> findBySubmissionId(long submissionId);
}
