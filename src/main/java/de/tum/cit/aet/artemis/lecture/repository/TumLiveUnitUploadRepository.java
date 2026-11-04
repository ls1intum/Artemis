package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveUnitUpload;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadedUnitDTO;

/**
 * The TUM.Live uploads of attachment video units. Every change locks the unit row first, which serializes it against the other changes of the same unit on
 * every node. A TUM.Live lecture leaves a unit only by moving to the cleanup queue in the same transaction.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface TumLiveUnitUploadRepository extends ArtemisJpaRepository<TumLiveUnitUpload, Long> {

    /** Why a pending upload is dropped when the unit's video was changed by hand while it converted. */
    String REPLACED_BY_EDIT = "The video of the unit was changed while the upload was converting.";

    Optional<TumLiveUnitUpload> findByLectureUnitId(long lectureUnitId);

    /**
     * The upload row of a unit, locked. The unit row's lock alone does not order changes once the unit is deleted, and a deleted unit's row is still changed by
     * the background job and by the deletion itself.
     *
     * @param lectureUnitId the attachment video unit
     * @return the locked row
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT upload FROM TumLiveUnitUpload upload WHERE upload.lectureUnitId = :lectureUnitId")
    Optional<TumLiveUnitUpload> findForUpdateByLectureUnitId(@Param("lectureUnitId") long lectureUnitId);

    List<TumLiveUnitUpload> findByCourseIdAndPendingGrantId(long courseId, long pendingGrantId);

    List<TumLiveUnitUpload> findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc();

    /**
     * Moves a pending upload to the back of the background job's queue, so waiting uploads do not keep finished ones from being checked.
     *
     * @param id  the row
     * @param ref the pending reference that was checked
     * @param now the current time
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("UPDATE TumLiveUnitUpload upload SET upload.pendingCheckedAt = :now WHERE upload.id = :id AND upload.pendingRef = :ref")
    void markPendingChecked(@Param("id") long id, @Param("ref") String ref, @Param("now") ZonedDateTime now);

    /**
     * The units of a course with a video uploaded to TUM.Live, attached or still pending: a pending one may be attached before the unit is deleted, so its delete
     * dialog asks about it too.
     *
     * @param courseId the course
     * @return the units and their lectures
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadedUnitDTO(lectureUnit.lecture.id, lectureUnit.id)
            FROM TumLiveUnitUpload upload, LectureUnit lectureUnit
            WHERE lectureUnit.id = upload.lectureUnitId
                AND lectureUnit.lecture.course.id = :courseId
                AND (upload.currentRef IS NOT NULL OR upload.pendingRef IS NOT NULL)
            """)
    List<TumLiveUploadedUnitDTO> findUploadedUnitsByCourseId(@Param("courseId") long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attachmentVideoUnit FROM AttachmentVideoUnit attachmentVideoUnit WHERE attachmentVideoUnit.id = :lectureUnitId")
    Optional<AttachmentVideoUnit> findAttachmentVideoUnitForUpdateById(@Param("lectureUnitId") long lectureUnitId);

    @Modifying
    @Query("UPDATE AttachmentVideoUnit attachmentVideoUnit SET attachmentVideoUnit.videoSource = :videoSource WHERE attachmentVideoUnit.id = :lectureUnitId")
    void updateVideoSource(@Param("lectureUnitId") long lectureUnitId, @Param("videoSource") String videoSource);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO tum_live_recording_cleanup (course_id, integration_id, grant_id, external_ref, next_attempt_at)
            VALUES (:courseId, :integrationId, :grantId, :externalRef, :nextAttemptAt)
            """)
    void insertCleanup(@Param("courseId") long courseId, @Param("integrationId") long integrationId, @Param("grantId") long grantId, @Param("externalRef") String externalRef,
            @Param("nextAttemptAt") ZonedDateTime nextAttemptAt);

    /**
     * Rows of units that were deleted, without a pending upload: left by a unit deleted without asking about TUM.Live (for example with its course), or by a
     * unit whose deletion has not released them yet.
     *
     * @return the rows
     */
    @Query("""
            SELECT upload.id FROM TumLiveUnitUpload upload
            WHERE upload.pendingRef IS NULL
                AND NOT EXISTS (SELECT lectureUnit.id FROM LectureUnit lectureUnit WHERE lectureUnit.id = upload.lectureUnitId)
            """)
    Set<Long> findIdsWithoutUnitAndPendingUpload();

    /**
     * Removes rows of deleted units that are still without a pending upload. Their recordings stay in TUM.Live.
     *
     * @param ids the rows, as found by {@link #findIdsWithoutUnitAndPendingUpload()}
     * @return the number of removed rows
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            DELETE FROM TumLiveUnitUpload upload
            WHERE upload.id IN :ids
                AND upload.pendingRef IS NULL
                AND NOT EXISTS (SELECT lectureUnit.id FROM LectureUnit lectureUnit WHERE lectureUnit.id = upload.lectureUnitId)
            """)
    int deleteWithoutUnitAndPendingUploadByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Starts an upload for a unit, or returns the one that is pending: a unit has at most one pending upload, and repeating the start must not leave the first
     * reference behind. A pending upload through another grant, made before the course was connected to TUM.Live again, moves to the cleanup queue instead.
     *
     * @param lectureUnitId the attachment video unit
     * @param integrationId the TUM.Live integration
     * @param grantId       the grant of the unit's course
     * @param courseSlug    the TUM.Live course slug of that grant
     * @param newRef        the reference to use for a new upload
     * @param now           the current time
     * @return the pending upload
     */
    @Transactional
    default TumLiveUnitUpload startPending(long lectureUnitId, long integrationId, long grantId, String courseSlug, String newRef, ZonedDateTime now) {
        Optional<AttachmentVideoUnit> lockedUnit = findAttachmentVideoUnitForUpdateById(lectureUnitId);
        if (lockedUnit.isEmpty()) {
            throw new EntityNotFoundException("AttachmentVideoUnit", lectureUnitId);
        }
        AttachmentVideoUnit unit = lockedUnit.get();
        TumLiveUnitUpload upload = findForUpdateByLectureUnitId(lectureUnitId).orElseGet(() -> {
            TumLiveUnitUpload created = new TumLiveUnitUpload();
            created.setLectureUnitId(lectureUnitId);
            created.setCourseId(unit.getLecture().getCourse().getId());
            created.setIntegrationId(integrationId);
            return created;
        });
        if (upload.getPendingRef() != null) {
            if (upload.getPendingGrantId() == grantId) {
                return upload;
            }
            insertCleanup(upload.getCourseId(), upload.getIntegrationId(), upload.getPendingGrantId(), upload.getPendingRef(), now);
            upload.clearPending();
        }
        upload.startPending(newRef, grantId, courseSlug, unit.getVideoSource(), now);
        return saveAndFlush(upload);
    }

    /**
     * Stores the TUM.Live lecture of the pending upload, if it is still the pending one.
     *
     * @param lectureUnitId the attachment video unit
     * @param ref           the pending reference
     * @param streamId      its TUM.Live lecture
     */
    @Transactional
    default void recordPendingStream(long lectureUnitId, String ref, long streamId) {
        findAttachmentVideoUnitForUpdateById(lectureUnitId).flatMap(ignored -> findForUpdateByLectureUnitId(lectureUnitId)).filter(upload -> ref.equals(upload.getPendingRef()))
                .ifPresent(upload -> {
                    upload.setPendingStreamId(streamId);
                    saveAndFlush(upload);
                });
    }

    /**
     * Gives up a pending upload: its TUM.Live lecture moves to the cleanup queue. Only exactly the given reference, only while it is still pending; the current
     * upload is never touched.
     *
     * @param lectureUnitId the attachment video unit
     * @param ref           the reference to give up
     * @param reason        why, shown to editors
     * @param now           the current time
     * @return whether the reference was pending and is given up now
     */
    @Transactional
    default boolean discardPending(long lectureUnitId, String ref, String reason, ZonedDateTime now) {
        Optional<AttachmentVideoUnit> unit = findAttachmentVideoUnitForUpdateById(lectureUnitId);
        Optional<TumLiveUnitUpload> pending = findForUpdateByLectureUnitId(lectureUnitId).filter(upload -> ref.equals(upload.getPendingRef()));
        if (pending.isEmpty()) {
            return false;
        }
        TumLiveUnitUpload upload = pending.get();
        insertCleanup(upload.getCourseId(), upload.getIntegrationId(), upload.getPendingGrantId(), ref, now);
        upload.clearPending();
        upload.setLastError(reason);
        if (unit.isEmpty()) {
            // The unit was deleted without asking about TUM.Live; its attached recording stays there.
            delete(upload);
        }
        else {
            saveAndFlush(upload);
        }
        return true;
    }

    /**
     * Attaches a converted upload: the unit gets the new video URL, but only if its URL is still the one it had when the upload started; otherwise a manual edit
     * won and the upload moves to the cleanup queue. The previously attached upload moves to the queue in the same step.
     *
     * @param lectureUnitId  the attachment video unit
     * @param ref            the pending reference that is ready
     * @param newVideoSource the TUM Live watch page URL of the upload
     * @param now            the current time
     * @return whether the unit shows the upload now
     */
    @Transactional
    default boolean attachPending(long lectureUnitId, String ref, String newVideoSource, ZonedDateTime now) {
        Optional<AttachmentVideoUnit> unit = findAttachmentVideoUnitForUpdateById(lectureUnitId);
        Optional<TumLiveUnitUpload> pending = findForUpdateByLectureUnitId(lectureUnitId).filter(upload -> ref.equals(upload.getPendingRef()));
        if (pending.isEmpty()) {
            return false;
        }
        TumLiveUnitUpload upload = pending.get();
        String videoSource = unit.map(AttachmentVideoUnit::getVideoSource).orElse(null);
        if (unit.isEmpty() || !Objects.equals(videoSource, upload.getExpectedVideoSource())) {
            discardPending(lectureUnitId, ref, REPLACED_BY_EDIT, now);
            return false;
        }
        updateVideoSource(lectureUnitId, newVideoSource);
        if (upload.getCurrentRef() != null) {
            insertCleanup(upload.getCourseId(), upload.getIntegrationId(), upload.getCurrentGrantId(), upload.getCurrentRef(), now);
        }
        upload.promotePending();
        upload.setLastError(null);
        saveAndFlush(upload);
        return true;
    }

    /**
     * Forgets the attached upload of a unit whose video link an editor changed to another video, so deleting the unit no longer offers to delete it in TUM.Live.
     * The recording stays there, as the editor did not ask to delete it.
     *
     * @param lectureUnitId  the attachment video unit
     * @param newVideoSource the link the edit wrote; nothing happens if the unit has another one by now, since a later edit decides then
     * @param shownStreamId  the TUM.Live lecture that link shows, or null if it shows none
     */
    @Transactional
    default void forgetCurrentUnlessShown(long lectureUnitId, String newVideoSource, Long shownStreamId) {
        Optional<AttachmentVideoUnit> unit = findAttachmentVideoUnitForUpdateById(lectureUnitId);
        if (unit.isEmpty() || !Objects.equals(unit.get().getVideoSource(), newVideoSource)) {
            return;
        }
        findForUpdateByLectureUnitId(lectureUnitId).filter(upload -> upload.getCurrentRef() != null && !Objects.equals(upload.getCurrentStreamId(), shownStreamId))
                .ifPresent(upload -> {
                    upload.clearCurrent();
                    saveAndFlush(upload);
                });
    }

    /**
     * Releases the uploads of a deleted unit: a pending upload always moves to the cleanup queue, the attached one only if the instructor chose to delete it in
     * TUM.Live too. Called after the unit was deleted, so nothing is queued if its deletion fails.
     *
     * @param lectureUnitId   the attachment video unit that was deleted
     * @param deleteRecording whether to delete the attached TUM.Live recording too
     * @param now             the current time
     */
    @Transactional
    default void releaseForDeletion(long lectureUnitId, boolean deleteRecording, ZonedDateTime now) {
        findAttachmentVideoUnitForUpdateById(lectureUnitId);
        findForUpdateByLectureUnitId(lectureUnitId).ifPresent(upload -> {
            if (upload.getPendingRef() != null) {
                insertCleanup(upload.getCourseId(), upload.getIntegrationId(), upload.getPendingGrantId(), upload.getPendingRef(), now);
            }
            if (deleteRecording && upload.getCurrentRef() != null) {
                insertCleanup(upload.getCourseId(), upload.getIntegrationId(), upload.getCurrentGrantId(), upload.getCurrentRef(), now);
            }
            delete(upload);
        });
    }
}
