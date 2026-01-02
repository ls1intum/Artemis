package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for cleaning up old and orphaned results.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ResultCleanupRepository extends ArtemisJpaRepository<Result, Long> {

    // TODO: remove the part "s.exampleSubmission IS FALSE" when examples submissions are migrated to example participations
    /**
     * Deletes {@link Result} entries that have no participation and no submission.
     * Now a result is considered orphaned if its submission is null
     * or if its submission exists but its participation is null.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Result r
            WHERE r.id IN (
                SELECT r2.id
                FROM Result r2
                    LEFT JOIN r2.submission s
                    LEFT JOIN s.participation p
                WHERE s IS NULL
                    OR (s.exampleSubmission IS FALSE AND p IS NULL)
            )
            """)
    int deleteResultWithoutParticipationAndSubmission();

    // TODO: remove the part "s.exampleSubmission IS FALSE" when examples submissions are migrated to example participations
    /**
     * Counts {@link Result} entries that have no participation and no submission.
     * Now a result is considered orphaned if its submission is null
     * or if its submission exists but its participation is null.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(r)
            FROM Result r
                LEFT JOIN r.submission s
                LEFT JOIN s.participation p
            WHERE s IS NULL
                OR (s.exampleSubmission IS FALSE AND p IS NULL)
            """)
    int countResultWithoutParticipationAndSubmission();
}
