package de.tum.cit.aet.artemis.lecture.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.ServedFileUrl;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A Attachment.
 */
@Entity
@Table(name = "attachment")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Attachment extends DomainObject implements Serializable {

    @Column(name = "name")
    private String name;

    @Column(name = "jhi_link")
    private String link;

    @Column(name = "version")
    private Integer version;

    @Column(name = "sha256_hash")
    private String sha256Hash;

    @Column(name = "upload_date")
    private ZonedDateTime uploadDate;

    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    private AttachmentType attachmentType;

    @ManyToOne
    @JsonIgnoreProperties("attachments")
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("attachments")
    private Lecture lecture;

    @OneToOne
    @JoinColumn(name = "attachment_unit_id")
    private AttachmentVideoUnit attachmentVideoUnit;

    // Student Version holds the version of the file without the pages hidden by the Instructor
    @Column(name = "student_version")
    private String studentVersion;

    @Column(name = "display_page_numbers")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> displayPageNumbers;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.strip() : null;
    }

    /**
     * The path the attachment file is served under, relative to {@code api/core/files/}.
     * <p>
     * The column stores only the filename; which of the two attachment endpoints serves it follows from the attachment itself, because an attachment either belongs to an
     * attachment video unit or hangs directly off a lecture. Before the owning row has an id there is no URL to give out, and the filename is returned instead.
     *
     * @return the served path of the attachment file, or its filename when the owner is not known yet
     */
    public String getLink() {
        if (attachmentVideoUnit != null && attachmentVideoUnit.getId() != null) {
            return ServedFileUrl.attachmentVideoUnitFile(attachmentVideoUnit.getId(), link);
        }
        return ServedFileUrl.lectureAttachment(lecture != null ? lecture.getId() : null, link);
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename} for why a served URL sent back by a client cannot end up in the column.
     *
     * @param link the filename of the attachment file, or the URL it is served under
     */
    public void setLink(String link) {
        this.link = FileSystemLocation.storedFilename(link);
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public ZonedDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(ZonedDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public AttachmentType getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(AttachmentType attachmentType) {
        this.attachmentType = attachmentType;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public AttachmentVideoUnit getAttachmentVideoUnit() {
        return attachmentVideoUnit;
    }

    public void setAttachmentVideoUnit(AttachmentVideoUnit attachmentVideoUnit) {
        this.attachmentVideoUnit = attachmentVideoUnit;
    }

    /**
     * The path the student version of the slides is served under, relative to {@code api/core/files/}. Only an attachment video unit has one.
     *
     * @return the served path of the student version, or its filename when the attachment video unit is not known yet
     */
    public String getStudentVersion() {
        return ServedFileUrl.studentVersionSlides(attachmentVideoUnit != null ? attachmentVideoUnit.getId() : null, studentVersion);
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename}.
     *
     * @param studentVersion the filename of the student version, or the URL it is served under
     */
    public void setStudentVersion(String studentVersion) {
        this.studentVersion = FileSystemLocation.storedFilename(studentVersion);
    }

    /**
     * Gets the display page numbers mapping for this attachment's PDF.
     * The list maps slide numbers to the displayed page numbers detected in the PDF:
     * Index 0 = displayed page number for slide 1, Index 1 = displayed page number for slide 2, etc.
     * A value of -1 indicates the slide has no detected displayed page number.
     *
     * @return list of displayed page numbers indexed by slide number (0-based), or null if not applicable
     */
    public List<Integer> getDisplayPageNumbers() {
        return displayPageNumbers;
    }

    /**
     * Sets the display page numbers mapping for this attachment's PDF.
     *
     * @param displayPageNumbers list of displayed page numbers indexed by slide number (0-based), or null
     */
    public void setDisplayPageNumbers(List<Integer> displayPageNumbers) {
        this.displayPageNumbers = displayPageNumbers;
    }

    public Boolean isVisibleToStudents() {
        if (releaseDate == null) {  // no release date means the attachment is visible to students
            return Boolean.TRUE;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    @Override
    public String toString() {
        return "Attachment{" + "id=" + getId() + ", name='" + getName() + "'" + ", link='" + getLink() + "'" + ", version='" + getVersion() + "'" + ", uploadDate='"
                + getUploadDate() + "'" + ", releaseDate='" + getReleaseDate() + "'" + ", attachmentType='" + getAttachmentType() + "'" + "}";
    }
}
