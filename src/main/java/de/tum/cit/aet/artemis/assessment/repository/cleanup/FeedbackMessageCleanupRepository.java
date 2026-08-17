package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
     * Deletes {@link FeedbackMessage} rows that are no longer referenced by any test-case or SCA feedback.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM FeedbackMessage m
            WHERE NOT EXISTS (SELECT 1 FROM TestCaseFeedback f WHERE f.message = m)
                AND NOT EXISTS (SELECT 1 FROM ScaFeedback f2 WHERE f2.message = m)
            """)
    int deleteUnreferencedFeedbackMessages();

    /**
     * Counts {@link FeedbackMessage} rows that are no longer referenced by any test-case or SCA feedback.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(m)
            FROM FeedbackMessage m
            WHERE NOT EXISTS (SELECT 1 FROM TestCaseFeedback f WHERE f.message = m)
                AND NOT EXISTS (SELECT 1 FROM ScaFeedback f2 WHERE f2.message = m)
            """)
    int countUnreferencedFeedbackMessages();
}
