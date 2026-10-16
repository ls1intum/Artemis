package de.tum.cit.aet.artemis.localci.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;

@Lazy
@Repository
@Primary
public interface BuildJobTestRepository extends BuildJobRepository {

    Optional<BuildJob> findBuildJobByResult(Result result);

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildJobIdDesc(Long participationId);

    /**
     * Counts the build jobs linked to the given result. The containers of a multi-container build link their jobs to the
     * result they merged into, so this is how many containers contributed to it.
     *
     * @param resultId the id of the aggregated result
     * @return the number of build jobs linked to the result
     */
    long countByResultId(long resultId);
}
