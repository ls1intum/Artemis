package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

/**
 * Spring Data JPA repository for the Transcription of a lecture video entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureTranscriptionRepository extends ArtemisJpaRepository<LectureTranscription, Long> {

    Optional<LectureTranscription> findByLectureUnit_Id(Long lectureUnitId);

    List<LectureTranscription> findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus status);

    /**
     * Find the transcriptions of the given units that are in the given status.
     *
     * @param lectureUnitIds the units to look at
     * @param status         the transcription status to match
     * @return the matching transcriptions
     */
    List<LectureTranscription> findAllByLectureUnit_IdInAndTranscriptionStatus(Collection<Long> lectureUnitIds, TranscriptionStatus status);

    Optional<LectureTranscription> findByJobId(String jobId);

    /**
     * Find all transcriptions for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return list of transcriptions for all units in the lecture
     */
    @Query("""
            SELECT t FROM LectureTranscription t
            JOIN FETCH t.lectureUnit lu
            WHERE lu.lecture.id = :lectureId
            """)
    List<LectureTranscription> findByLectureId(@Param("lectureId") Long lectureId);

    /**
     * Read and lock the processing state of a unit, but only while the given job token still owns it.
     *
     * @param lectureUnitId the unit whose processing state to lock
     * @param token         the job token that must still own the unit
     * @return the locked state, or empty when the token no longer owns the unit
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.lectureUnit.id = :lectureUnitId
            AND ps.ingestionJobToken = :token
            """)
    Optional<LectureUnitProcessingState> lockProcessingStateIfTokenMatches(@Param("lectureUnitId") long lectureUnitId, @Param("token") String token);

    /**
     * Write a transcription checkpoint, but only while the run that produced it still owns the unit.
     * <p>
     * The ownership check and the write commit in one transaction under the processing-state row lock. Every write that ends or replaces
     * the run (a content change invalidating its token, a recovery, a completion) is a conditional update of that same row, so it waits for
     * this transaction and this transaction waits for it: a superseded run can neither recreate a transcript a content change deleted, nor
     * overwrite the transcript of the run that replaced it. The transcript is read only after the lock is held, so the check below sees
     * what is committed now (on MySQL the transaction's snapshot is taken by this first plain read, after the locking read; on PostgreSQL
     * every statement reads the latest committed data), and concurrent first checkpoints of one unit are serialized instead of colliding
     * on the unique unit column. A completed transcript is never replaced by a pending one.
     *
     * @param lectureUnit the unit the transcription belongs to
     * @param token       the job token the checkpoint carried
     * @param language    the checkpoint's language
     * @param segments    the checkpoint's segments
     * @param status      the status to store
     * @return true if the transcription was written; false when the token no longer owns the unit, or a pending checkpoint met a completed
     *         transcript
     */
    @Transactional // ok because the ownership check and the write must commit together under the state row lock
    default boolean saveCheckpointIfTokenMatches(LectureUnit lectureUnit, String token, String language, List<LectureTranscriptionSegment> segments, TranscriptionStatus status) {
        if (lockProcessingStateIfTokenMatches(lectureUnit.getId(), token).isEmpty()) {
            return false;
        }
        LectureTranscription transcription = findByLectureUnit_Id(lectureUnit.getId()).orElseGet(() -> new LectureTranscription(language, segments, lectureUnit));
        if (transcription.getTranscriptionStatus() == TranscriptionStatus.COMPLETED && status != TranscriptionStatus.COMPLETED) {
            return false;
        }
        transcription.setLanguage(language);
        transcription.setSegments(segments);
        transcription.setTranscriptionStatus(status);
        save(transcription);
        return true;
    }

    /**
     * Delete a unit's transcription while the recovery that claimed its interrupted content change still holds that claim
     * (see {@link LectureUnitProcessingStateRecoveryRepository#claimStrandedRun}). A concurrent requeue of the unit clears the
     * claim, so once a newer run may own the unit this matches nothing and leaves its transcript alone. JPQL rather than a
     * native {@code DELETE ... EXISTS}, so Hibernate emits the dialect's own single-table delete: the native form removed the
     * processing-state row as well on MySQL.
     *
     * @param lectureUnitId the unit whose transcription to delete
     * @param claimToken    the recovery's claim
     * @return the number of deleted rows
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            DELETE FROM LectureTranscription t
            WHERE t.lectureUnit.id = :lectureUnitId
            AND EXISTS (
                SELECT ps.id FROM LectureUnitProcessingState ps
                WHERE ps.lectureUnit.id = :lectureUnitId AND ps.claimToken = :claimToken AND ps.ingestionJobToken IS NULL
            )
            """)
    int deleteIfRecoveryClaimHolds(@Param("lectureUnitId") Long lectureUnitId, @Param("claimToken") String claimToken);
}
