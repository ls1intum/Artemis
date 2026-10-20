package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveRecordingCleanup;

@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface TumLiveRecordingCleanupRepository extends ArtemisJpaRepository<TumLiveRecordingCleanup, Long> {

    List<TumLiveRecordingCleanup> findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(ZonedDateTime now);

    List<TumLiveRecordingCleanup> findByGrantId(long grantId);

    /**
     * Postpones a deletion TUM.Live could not confirm.
     *
     * @param id            the queue entry
     * @param nextAttemptAt when to try again
     * @param blockedReason why TUM.Live refused it, or null for a transient failure
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE TumLiveRecordingCleanup cleanup SET cleanup.nextAttemptAt = :nextAttemptAt, cleanup.blockedReason = :blockedReason WHERE cleanup.id = :id")
    void postpone(@Param("id") long id, @Param("nextAttemptAt") ZonedDateTime nextAttemptAt, @Param("blockedReason") String blockedReason);

    /**
     * Removes a deletion TUM.Live confirmed.
     *
     * @param id the queue entry
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("DELETE FROM TumLiveRecordingCleanup cleanup WHERE cleanup.id = :id")
    void deleteConfirmed(@Param("id") long id);
}
