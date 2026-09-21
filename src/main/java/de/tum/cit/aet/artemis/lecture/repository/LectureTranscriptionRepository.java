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
}
