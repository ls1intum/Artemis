package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * Spring Data JPA repository for the BuildLogEntry entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface BuildLogEntryRepository extends ArtemisJpaRepository<BuildLogEntry, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteByProgrammingSubmissionId(long programmingSubmissionId);

    @Transactional // ok because of delete
    @Modifying
    void deleteByProgrammingSubmissionIdAndResultIdAndContainerName(long programmingSubmissionId, long resultId, String containerName);

    /**
     * Deletes the build logs of a submission that belong to a build that is over: the logs of single-container builds,
     * which carry no result, and the logs of multi-container builds whose aggregated result has completed. The logs of
     * a multi-container build of the same submission that is still merging are kept.
     *
     * @param submissionId the id of the submission
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM BuildLogEntry entry
            WHERE entry.programmingSubmission.id = :submissionId
                AND (entry.resultId IS NULL OR entry.resultId NOT IN (
                    SELECT result.id
                    FROM Result result
                    WHERE result.submission.id = :submissionId
                        AND result.completionDate IS NULL))
            """)
    void deleteLogsOfFinishedBuilds(@Param("submissionId") long submissionId);

}
