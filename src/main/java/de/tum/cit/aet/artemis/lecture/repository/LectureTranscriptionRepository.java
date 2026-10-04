package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
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
     * Update an existing transcription's content, atomically conditional on the run that produced it still owning
     * the unit's processing state at the instant the update commits: the same guard as {@link #insertIfTokenMatches}.
     * <p>
     * Keying on the row's id alone only covers a requeue that deleted the row. A lease reclaim or retry keeps the row
     * and starts a newer run on it, so a delayed checkpoint of the superseded run would otherwise overwrite the newer
     * run's transcript before its own stale token is rejected anywhere else. {@code FOR UPDATE} on the ownership
     * subquery serializes this write with any transaction changing that token, as it does for the insert.
     * <p>
     * Within one run the token cannot tell a raw checkpoint from the enriched one, so a raw write delayed past the enriched
     * write would otherwise replace the completed transcript with its PENDING segments. A COMPLETED transcript is therefore
     * only ever replaced by another COMPLETED one. The condition is on the updated row itself, so both databases check it
     * against the latest committed version under the row lock, however the two writes interleave.
     *
     * @param id                  the transcription row a checkpoint's earlier read found
     * @param lectureUnitId       the unit this transcription belongs to
     * @param language            the checkpoint's language
     * @param segments            the checkpoint's segments, pre-serialized the same way the entity's own converter
     *                                would (native queries bypass the ORM type layer)
     * @param transcriptionStatus the status to set, as its enum name
     * @param expectedToken       the token the checkpoint carried
     * @return 1 when applied, 0 when the row no longer exists, the token had already changed, or a PENDING write met a
     *         COMPLETED transcript
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query(value = """
            UPDATE lecture_transcription
            SET language = :language, segments = CAST(:segments AS json), transcription_status = :transcriptionStatus
            WHERE id = :id
            AND (transcription_status <> 'COMPLETED' OR :transcriptionStatus = 'COMPLETED')
            AND EXISTS (
                SELECT 1 FROM lecture_unit_processing_state
                WHERE lecture_unit_id = :lectureUnitId AND ingestion_job_token = :expectedToken
                FOR UPDATE
            )
            """, nativeQuery = true)
    int updateContentIfTokenMatches(@Param("id") Long id, @Param("lectureUnitId") Long lectureUnitId, @Param("language") String language, @Param("segments") String segments,
            @Param("transcriptionStatus") String transcriptionStatus, @Param("expectedToken") String expectedToken);

    /**
     * Insert a unit's first transcription row, atomically conditional on the run that produced it
     * still owning the unit's processing state at the instant the insert commits -- not at some
     * earlier read, and not merely at the instant its own statement began.
     * <p>
     * A first checkpoint has no existing row to guard an {@code UPDATE} on (see
     * {@link #updateContentIfTokenMatches}), so a read-then-insert shape always leaves a gap between
     * checking the token and writing: a content-triggered requeue can invalidate the token and find
     * no row to delete in exactly that gap, and an unconditioned insert afterward would persist stale
     * content the fresh generation could mistake for its own. Folding the check into the {@code EXISTS}
     * subquery closes that gap for a token change that lands before this statement starts, but a plain
     * subquery is still only a non-locking snapshot: a concurrent invalidation could otherwise commit
     * between this statement's snapshot and its own commit, and neither write would ever see the
     * other. {@code FOR UPDATE} on the subquery closes that remaining window -- it blocks until any
     * transaction holding the matching processing-state row's lock (such as
     * {@link LectureUnitProcessingStateRepository#invalidateTokenIfMatches}) commits or rolls back,
     * and then re-checks the row's now-current state before this insert proceeds, so the two writes
     * are serialized on the same row instead of racing past each other. JPQL has no bulk insert
     * statement, so this is a native, portable {@code INSERT ... SELECT ... WHERE EXISTS}, which folds
     * the ownership check into the same statement as the write instead of a separate query before it.
     *
     * @param lectureUnitId       the unit this transcription belongs to
     * @param language            the checkpoint's language
     * @param segments            the checkpoint's segments, pre-serialized the same way the entity's
     *                                own converter would (native queries bypass the ORM type layer)
     * @param transcriptionStatus the status to set, as its enum name
     * @param expectedToken       the token ownership was proven under
     * @return 1 when inserted, 0 when the processing state's token had already changed
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query(value = """
            INSERT INTO lecture_transcription (language, segments, transcription_status, lecture_unit_id)
            SELECT :language, CAST(:segments AS json), :transcriptionStatus, :lectureUnitId
            WHERE EXISTS (
                SELECT 1 FROM lecture_unit_processing_state
                WHERE lecture_unit_id = :lectureUnitId AND ingestion_job_token = :expectedToken
                FOR UPDATE
            )
            """, nativeQuery = true)
    int insertIfTokenMatches(@Param("lectureUnitId") Long lectureUnitId, @Param("language") String language, @Param("segments") String segments,
            @Param("transcriptionStatus") String transcriptionStatus, @Param("expectedToken") String expectedToken);

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
