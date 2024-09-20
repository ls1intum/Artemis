package de.tum.cit.aet.artemis.programming.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;

@Repository
@Primary
public interface BuildJobTestRepository extends BuildJobRepository {

    Optional<BuildJob> findBuildJobByResult(Result result);

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);
}
