package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/** Read operations for every result of a single submission, such as when a multi-container build is finalized. */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface SubmissionResultRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * All results of a submission, newest first.
     *
     * @param submissionId the id of the submission
     * @return the submission's results, newest first
     */
    List<Result> findAllBySubmissionIdOrderByIdDesc(long submissionId);
}
