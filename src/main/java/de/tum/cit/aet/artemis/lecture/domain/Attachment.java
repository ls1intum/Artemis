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

    /**
     * The lecture whose directory the attachment file lies in, or null when the file lies in the directory of the attachment video unit that owns it.
     * <p>
     * An attachment can no longer be attached to a lecture, and every attachment that once was now belongs to an attachment video unit. Their files stayed under
     * {@code uploads/attachments/lecture/{lectureId}} because a changelog cannot move files, so this reference is what says where such a file is: it is the metadata the
     * location needs, exactly as the unit id is for every other attachment. It is set by the migration and cleared again as soon as the file is replaced through the unit
     * editor, which writes the new file into the unit's own directory. Nothing but {@link #fileLocation()} and the route that serves those files reads it, and it is not part
     * of the REST boundary, so a client cannot repoint an attachment at another lecture's directory.
     */
    @ManyToOne
    @JsonIgnore
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
     * The column stores only the filename; which of the two attachment endpoints serves it follows from the attachment itself. An attachment that belongs to an attachment
     * video unit is served under that unit, whichever directory its file happens to lie in, so a client sees one URL shape for every unit and the unit routes all answer for
     * it. This is where the decoupling shows most plainly: the URL follows the unit while {@link #fileLocation()} follows the lecture for a migrated attachment, and the two
     * answers no longer have to agree. Before the owning row has an id there is no URL to give out, and the filename is returned instead.
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
     * Where the attachment file lies on disk.
     * <p>
     * Which of the two attachment directories holds the file follows from the attachment itself and from nothing else: an attachment migrated from a lecture keeps its file
     * under that lecture's directory and names the lecture, and every other attachment has its file under the directory of the attachment video unit that owns it. Reading it
     * off the stored value instead put a file in the wrong directory whenever that value was written in the other of the two spellings the same endpoint answers to, which is
     * why nothing here looks at the value at all.
     *
     * @return the location of the attachment file
     */
    @JsonIgnore
    public FileSystemLocation fileLocation() {
        if (lecture != null && lecture.getId() != null) {
            return new FileSystemLocation.LectureAttachment(lecture.getId(), link);
        }
        if (attachmentVideoUnit != null && attachmentVideoUnit.getId() != null) {
            return new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnit.getId(), link);
        }
        throw new IllegalStateException("Attachment " + getId() + " names neither a lecture nor a persisted attachment video unit, so its file cannot be located");
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
