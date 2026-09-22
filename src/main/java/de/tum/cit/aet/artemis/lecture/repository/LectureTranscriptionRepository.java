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
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
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
     * Update an existing transcription's content, atomically: only while the row a checkpoint's
     * earlier read found still exists.
     * <p>
     * A content-triggered requeue (video changed) can delete this unit's transcription between that
     * read and the write a checkpoint callback makes once it has proven ownership of its run (see
     * {@code ProcessingStateCallbackService#saveTranscription}). Conditioning this write on the row's
     * own id -- rather than relying on JPA's save/merge semantics for a since-deleted identity, which
     * silently recreates the row -- is what turns that deletion into a no-op here instead of stale
     * content reappearing under a new id.
     *
     * @param id                  the transcription row a checkpoint's earlier read found
     * @param language            the checkpoint's language
     * @param segments            the checkpoint's segments
     * @param transcriptionStatus the status to set
     * @return 1 when applied, 0 when the row no longer exists
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureTranscription t
            SET t.language = :language, t.segments = :segments, t.transcriptionStatus = :transcriptionStatus
            WHERE t.id = :id
            """)
    int updateContentIfExists(@Param("id") Long id, @Param("language") String language, @Param("segments") List<LectureTranscriptionSegment> segments,
            @Param("transcriptionStatus") TranscriptionStatus transcriptionStatus);

    /**
     * Insert a unit's first transcription row, atomically conditional on the run that produced it
     * still owning the unit's processing state at the instant of the insert itself -- not at some
     * earlier read.
     * <p>
     * A first checkpoint has no existing row to guard an {@code UPDATE} on (see
     * {@link #updateContentIfExists}), so a read-then-insert shape always leaves a gap between
     * checking the token and writing: a content-triggered requeue can invalidate the token and find
     * no row to delete in exactly that gap, and an unconditioned insert afterward would persist stale
     * content the fresh generation could mistake for its own. JPQL has no bulk insert statement, so
     * this is a native, portable {@code INSERT ... SELECT ... WHERE EXISTS}, which folds the ownership
     * check into the same statement as the write instead of a separate query before it.
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
            )
            """, nativeQuery = true)
    int insertIfTokenMatches(@Param("lectureUnitId") Long lectureUnitId, @Param("language") String language, @Param("segments") String segments,
            @Param("transcriptionStatus") String transcriptionStatus, @Param("expectedToken") String expectedToken);
}
