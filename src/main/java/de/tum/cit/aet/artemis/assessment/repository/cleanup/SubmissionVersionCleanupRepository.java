package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;

/**
 * Spring Data JPA repository for cleaning up old submission versions.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SubmissionVersionCleanupRepository extends ArtemisJpaRepository<SubmissionVersion, Long> {

    /**
     * Deletes {@link SubmissionVersion} entities where the created date is after {@code deleteFrom}
     * and before {@code deleteTo}.
     *
     * @param deleteFrom the start date for selecting submissions
     * @param deleteTo   the end date for selecting submissions
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM SubmissionVersion sv
            WHERE sv.createdDate > :deleteFrom
                AND sv.createdDate < :deleteTo
            """)
    int deleteSubmissionVersionsByCreatedDateRange(@Param("deleteFrom") Instant deleteFrom, @Param("deleteTo") Instant deleteTo);

    /**
     * Counts {@link SubmissionVersion} entities where the created date is after {@code deleteFrom}
     * and before {@code deleteTo}.
     *
     * @param deleteFrom the start date for selecting submissions
     * @param deleteTo   the end date for selecting submissions
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(sv)
            FROM SubmissionVersion sv
            WHERE sv.createdDate > :deleteFrom
                AND sv.createdDate < :deleteTo
            """)
    int countSubmissionVersionsByCreatedDateRange(@Param("deleteFrom") Instant deleteFrom, @Param("deleteTo") Instant deleteTo);

}
