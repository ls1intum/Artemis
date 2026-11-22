package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * The TUM.Live lectures Artemis uploaded for one attachment video unit: the one whose video the unit shows (current) and an upload that is not attached yet
 * (pending). Each is named by the reference Artemis chose for it in TUM.Live. Every change happens under the locks of the unit row and of this row, see
 * {@link de.tum.cit.aet.artemis.lecture.repository.TumLiveUnitUploadRepository}.
 */
@Entity
@Table(name = "tum_live_unit_upload")
public class TumLiveUnitUpload extends DomainObject {

    @Column(name = "lecture_unit_id", nullable = false, unique = true)
    @Parent
    private long lectureUnitId;

    /** The unit's course, so its uploads can still be found after the unit is gone, for example when the course is disconnected from TUM.Live. */
    @Column(name = "course_id", nullable = false)
    private long courseId;

    @Column(name = "integration_id", nullable = false)
    private long integrationId;

    @Column(name = "current_ref", length = 64)
    private String currentRef;

    @Column(name = "current_grant_id")
    private Long currentGrantId;

    @Column(name = "current_stream_id")
    private Long currentStreamId;

    @Column(name = "pending_ref", length = 64)
    private String pendingRef;

    @Column(name = "pending_grant_id")
    private Long pendingGrantId;

    @Column(name = "pending_stream_id")
    private Long pendingStreamId;

    @Column(name = "pending_since")
    private ZonedDateTime pendingSince;

    /** When the background job last looked at the pending upload; it takes the least recently checked ones first. */
    @Column(name = "pending_checked_at")
    private ZonedDateTime pendingCheckedAt;

    /** The TUM.Live course slug of the connection the upload was made through, for its watch page URL. */
    @Column(name = "pending_course_slug")
    private String pendingCourseSlug;

    /** The unit's video URL when the pending upload started; it is attached only if the URL is still this one. */
    @Column(name = "expected_video_source")
    private String expectedVideoSource;

    @Column(name = "last_error", length = 255)
    private String lastError;

    public long getLectureUnitId() {
        return lectureUnitId;
    }

    public void setLectureUnitId(long lectureUnitId) {
        this.lectureUnitId = lectureUnitId;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public long getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(long integrationId) {
        this.integrationId = integrationId;
    }

    public String getCurrentRef() {
        return currentRef;
    }

    public Long getCurrentGrantId() {
        return currentGrantId;
    }

    public Long getCurrentStreamId() {
        return currentStreamId;
    }

    public String getPendingRef() {
        return pendingRef;
    }

    public Long getPendingGrantId() {
        return pendingGrantId;
    }

    public Long getPendingStreamId() {
        return pendingStreamId;
    }

    public void setPendingStreamId(Long pendingStreamId) {
        this.pendingStreamId = pendingStreamId;
    }

    public ZonedDateTime getPendingSince() {
        return pendingSince;
    }

    public String getPendingCourseSlug() {
        return pendingCourseSlug;
    }

    public String getExpectedVideoSource() {
        return expectedVideoSource;
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the reason shown to editors, cut to the column's 255 characters: a conversion error from TUM.Live alone may already be that long.
     *
     * @param lastError the reason, or null
     */
    public void setLastError(String lastError) {
        this.lastError = lastError == null || lastError.codePointCount(0, lastError.length()) <= 255 ? lastError : lastError.substring(0, lastError.offsetByCodePoints(0, 255));
    }

    /**
     * Starts a pending upload.
     *
     * @param ref                 the new reference
     * @param grantId             the grant it is made through
     * @param courseSlug          the TUM.Live course slug of that grant
     * @param expectedVideoSource the unit's current video URL
     * @param since               when it started
     */
    public void startPending(String ref, long grantId, String courseSlug, String expectedVideoSource, ZonedDateTime since) {
        this.pendingRef = ref;
        this.pendingGrantId = grantId;
        this.pendingCourseSlug = courseSlug;
        this.pendingCheckedAt = since;
        this.pendingStreamId = null;
        this.expectedVideoSource = expectedVideoSource;
        this.pendingSince = since;
        this.lastError = null;
    }

    /**
     * Makes the pending upload the current one.
     */
    public void promotePending() {
        this.currentRef = pendingRef;
        this.currentGrantId = pendingGrantId;
        this.currentStreamId = pendingStreamId;
        clearPending();
    }

    /**
     * Forgets the attached upload, after the unit's video link was changed to another video. The recording stays in TUM.Live.
     */
    public void clearCurrent() {
        this.currentRef = null;
        this.currentGrantId = null;
        this.currentStreamId = null;
    }

    /**
     * Forgets the pending upload, after it was attached or handed to cleanup.
     */
    public void clearPending() {
        this.pendingRef = null;
        this.pendingGrantId = null;
        this.pendingStreamId = null;
        this.pendingSince = null;
        this.pendingCheckedAt = null;
        this.pendingCourseSlug = null;
        this.expectedVideoSource = null;
    }
}
