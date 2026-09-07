package de.tum.cit.aet.artemis.lecture.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.ServedFileUrl;

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
     * The column stores only the filename, and the URL is always the one of the attachment video unit that owns the attachment, whichever directory the file happens to lie in.
     * This is where the decoupling shows most plainly: the URL follows the unit while {@link #fileLocation()} may still answer with the lecture directory of a file the
     * migration left there, and the two answers no longer have to agree. Before the owning unit has an id there is no URL to give out, and the filename is returned instead.
     *
     * @return the served path of the attachment file, or its filename when the owning unit is not known yet
     */
    public String getLink() {
        return ServedFileUrl.attachmentVideoUnitFile(attachmentVideoUnit != null ? attachmentVideoUnit.getId() : null, link);
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

    public AttachmentVideoUnit getAttachmentVideoUnit() {
        return attachmentVideoUnit;
    }

    public void setAttachmentVideoUnit(AttachmentVideoUnit attachmentVideoUnit) {
        this.attachmentVideoUnit = attachmentVideoUnit;
    }

    /**
     * Where the attachment file lies on disk.
     * <p>
     * The owning attachment video unit is what says where the file is, and nothing else. Reading it off the stored value instead put a file in the wrong directory whenever that
     * value was written in the other of the two spellings the same endpoint answers to, which is why nothing here looks at the value at all.
     * <p>
     * The lecture is passed along only for the transitional fallback in {@link FileSystemLocation#ofAttachment}: a file the lecture migration left under
     * {@code uploads/attachments/lecture/{lectureId}} is still there until the migration entry that moves it has run. It is reached as metadata through the unit rather than
     * stored on the attachment; {@code LectureUnit.lecture} is a non-optional eager association, so this costs no query that loading the attachment did not already make.
     *
     * @return the location of the attachment file
     */
    @JsonIgnore
    public FileSystemLocation fileLocation() {
        if (attachmentVideoUnit == null || attachmentVideoUnit.getId() == null) {
            throw new IllegalStateException("Attachment " + getId() + " names no persisted attachment video unit, so its file cannot be located");
        }
        Lecture lecture = attachmentVideoUnit.getLecture();
        return FileSystemLocation.ofAttachment(attachmentVideoUnit.getId(), lecture != null ? lecture.getId() : null, link);
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
