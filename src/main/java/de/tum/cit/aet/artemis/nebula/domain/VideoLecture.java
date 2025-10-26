package de.tum.cit.aet.artemis.nebula.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * A VideoLecture entity stores metadata about lecture videos uploaded to the Nebula Video Storage Service.
 * This is a separate entity with a OneToOne relationship to Lecture to maintain modularity.
 */
@Entity
@Table(name = "video_lecture")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VideoLecture extends DomainObject {

    @OneToOne
    @JoinColumn(name = "lecture_id", unique = true)
    @JsonIgnoreProperties(value = { "lectureUnits", "attachments", "posts" }, allowSetters = true)
    private Lecture lecture;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(name = "filename")
    private String filename;

    @Column(name = "playlist_url", nullable = false)
    private String playlistUrl;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "uploaded_at")
    private ZonedDateTime uploadedAt;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPlaylistUrl() {
        return playlistUrl;
    }

    public void setPlaylistUrl(String playlistUrl) {
        this.playlistUrl = playlistUrl;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public ZonedDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(ZonedDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    @Override
    public String toString() {
        return "VideoLecture{" + "id=" + getId() + ", videoId='" + videoId + "'" + ", filename='" + filename + "'" + ", durationSeconds=" + durationSeconds + ", uploadedAt='"
                + uploadedAt + "'" + "}";
    }
}
