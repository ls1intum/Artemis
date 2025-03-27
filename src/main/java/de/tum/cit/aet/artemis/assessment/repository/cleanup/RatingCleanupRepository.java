package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Rating;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for cleaning up old and orphaned ratings.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Repository
public interface RatingCleanupRepository extends ArtemisJpaRepository<Rating, Long> {

    /**
     * Deletes {@link Rating} entries where the associated {@link Result} has no submission
     * or its submission has no participation.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE
            FROM Rating rt
            WHERE rt.result IN (
                SELECT r
                FROM Result r
                WHERE r.submission IS NULL
                    OR r.submission.participation IS NULL
                )
            """)
    int deleteOrphanRating();

    /**
     * Counts {@link Rating} entries where the associated {@link Result} has no submission
     * or its submission has no participation.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(rt)
            FROM Rating rt
            WHERE rt.result IN (
                SELECT r
                FROM Result r
                WHERE r.submission IS NULL
                    OR r.submission.participation IS NULL
                )
            """)
    int countOrphanRating();
}
