package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@Table(name = "slide")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Slide extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "attachment_unit_id", nullable = false)
    @Parent
    private AttachmentVideoUnit attachmentVideoUnit;

    /**
     * Whether a newer version of the file has replaced this slide, or the instructor dropped it from the page order.
     * <p>
     * A superseded slide keeps its unit rather than being detached from it. Nothing references a slide row, so a
     * detached one was reachable from nowhere and nothing ever removed it; keeping the unit leaves the history
     * readable and the row inside the unit's lifetime. Every query that lists a unit's slides filters these out.
     */
    @Column(name = "superseded", nullable = false)
    private boolean superseded = false;

    @Size(max = 150)
    @Column(name = "slide_image_path", length = 150)
    private String slideImagePath;

    @Column(name = "slide_number")
    private int slideNumber;

    @Column(name = "hidden")
    private ZonedDateTime hidden;

    /**
     * The version of the attachment file this slide was split from. Together with {@link #attachmentSha256Hash} it tells whether the slide belongs to the stored file, which
     * the student version needs: it removes hidden slides from that file by slide number.
     */
    @JsonIgnore
    @Column(name = "attachment_version")
    private Integer attachmentVersion;

    /**
     * The SHA-256 hash of the attachment file this slide was split from, see {@link #attachmentVersion}.
     */
    @JsonIgnore
    @Column(name = "attachment_sha256_hash", length = 64)
    private String attachmentSha256Hash;

    // Exercise ID is retained to sync the Slide's hidden date with the Exercise's due date when linked
    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    public Integer getAttachmentVersion() {
        return attachmentVersion;
    }

    public String getAttachmentSha256Hash() {
        return attachmentSha256Hash;
    }

    /**
     * Records that this slide was split from the given file revision of its attachment.
     *
     * @param version    the version of the attachment file
     * @param sha256Hash the hash of the attachment file
     */
    public void setAttachmentRevision(Integer version, String sha256Hash) {
        this.attachmentVersion = version;
        this.attachmentSha256Hash = sha256Hash;
    }

    /**
     * Whether this slide was split from the given file revision.
     *
     * @param version    the version of the attachment file
     * @param sha256Hash the hash of the attachment file
     * @return whether version and hash match, treating two missing values as equal
     */
    public boolean belongsToRevision(Integer version, String sha256Hash) {
        return Objects.equals(attachmentVersion, version) && Objects.equals(attachmentSha256Hash, sha256Hash);
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public void setSuperseded(boolean superseded) {
        this.superseded = superseded;
    }

    public AttachmentVideoUnit getAttachmentVideoUnit() {
        return attachmentVideoUnit;
    }

    public void setAttachmentVideoUnit(AttachmentVideoUnit attachmentVideoUnit) {
        this.attachmentVideoUnit = attachmentVideoUnit;
    }

    /**
     * The filename of the rendered slide image, which is the whole of what is stored.
     * <p>
     * Unlike the other file references this one is never turned into a URL, because no endpoint serves a slide by its filename. A client asks for a slide image by id through
     * {@code files/slides/{slideId}}, and the server locates the file from this slide's attachment video unit and slide number. The value is therefore a pure storage key.
     *
     * @return the filename of the slide image
     */
    public String getSlideImagePath() {
        return slideImagePath;
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename}.
     *
     * @param slideImagePath the filename of the slide image
     */
    public void setSlideImagePath(String slideImagePath) {
        this.slideImagePath = FileSystemLocation.storedFilename(slideImagePath);
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    public ZonedDateTime getHidden() {
        return hidden;
    }

    public void setHidden(ZonedDateTime hidden) {
        this.hidden = hidden;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }
}
