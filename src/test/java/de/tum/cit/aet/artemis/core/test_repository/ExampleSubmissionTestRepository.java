package de.tum.cit.aet.artemis.core.test_repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
public interface ExampleSubmissionTestRepository extends ArtemisJpaRepository<ExampleSubmission, Long> {

    Optional<ExampleSubmission> findBySubmissionId(long submissionId);
}
