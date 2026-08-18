package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for garbage-collecting unreferenced {@link FeedbackMessage} rows.
 * <p>
 * The referencing columns carry no database foreign key (by design, to avoid an index over tens of
 * millions of rows), and feedback rows can be deleted by a database-level cascade without the application
 * running — so unreferenced messages must be collected by this scan, which runs as part of the scheduled
 * data cleanup.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeedbackMessageCleanupRepository extends ArtemisJpaRepository<FeedbackMessage, Long> {

    /**
     * Deletes {@link FeedbackMessage} rows that are no longer referenced by any test-case or SCA feedback
     * and were created before the given cutoff. The cutoff is the race-safety window: build-result
     * processing commits the message row before the feedback rows that reference it (there is no
     * surrounding transaction), so a freshly created message may look unreferenced for a moment.
     *
     * @param createdBefore only messages created before this timestamp are deleted
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM FeedbackMessage m
            WHERE m.createdDate < :createdBefore
                AND NOT EXISTS (SELECT 1 FROM TestCaseFeedback f WHERE f.message = m)
                AND NOT EXISTS (SELECT 1 FROM ScaFeedback f2 WHERE f2.message = m)
            """)
    int deleteUnreferencedFeedbackMessages(@Param("createdBefore") ZonedDateTime createdBefore);
}
