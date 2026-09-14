package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the deduplicated, content-addressed {@link FeedbackMessage} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeedbackMessageRepository extends ArtemisJpaRepository<FeedbackMessage, Long> {

    Optional<FeedbackMessage> findByHash(byte[] hash);

    /**
     * Reads the message with the given hash with a shared lock, i.e. as a locking read. Unlike a plain read, a locking
     * read returns the latest committed row even when the surrounding transaction's snapshot predates that commit
     * (MySQL's REPEATABLE READ), which is what a caller that has just inserted the row in a transaction of its own needs
     * in order to read it back into its own persistence context. Requires an active transaction.
     *
     * @param hash the hash of the message text
     * @return the message, if a row with that hash exists
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT m
            FROM FeedbackMessage m
            WHERE m.hash = :hash
            """)
    Optional<FeedbackMessage> findByHashForShare(@Param("hash") byte[] hash);

    /**
     * Refreshes the garbage-collection grace timestamp of a reused message row. Returns 0 if the row no
     * longer exists — i.e. it lost against a concurrent garbage collection — in which case the caller must
     * re-create the message instead of referencing the deleted row.
     *
     * @param messageId the id of the reused message row
     * @param now       the new grace timestamp
     * @return the number of updated rows (0 or 1)
     */
    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE FeedbackMessage m
            SET m.createdDate = :now
            WHERE m.id = :messageId
            """)
    int refreshCreatedDate(@Param("messageId") long messageId, @Param("now") ZonedDateTime now);
}
