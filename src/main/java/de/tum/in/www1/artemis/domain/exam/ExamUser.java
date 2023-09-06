package de.tum.in.www1.artemis.domain.exam;

import java.nio.file.Path;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.EntityFileService;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

@Entity
@Table(name = "exam_user")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamUser extends AbstractAuditingEntity {

    @Transient
    private final transient FilePathService filePathService = new FilePathService();

    @Transient
    private final transient FileService fileService = new FileService();

    @Transient
    private final transient EntityFileService entityFileService = new EntityFileService(fileService, filePathService);

    @Transient
    private String prevSigningImagePath;

    @Transient
    private String prevStudentImagePath;

    @Column(name = "actual_room")
    private String actualRoom;

    @Column(name = "actual_seat")
    private String actualSeat;

    @Column(name = "planned_room")
    private String plannedRoom;

    @Column(name = "planned_seat")
    private String plannedSeat;

    @Column(name = "did_check_image")
    private boolean didCheckImage = false;

    @Column(name = "did_check_name")
    private boolean didCheckName = false;

    @Column(name = "did_check_login")
    private boolean didCheckLogin = false;

    @Column(name = "did_check_registration_number")
    private boolean didCheckRegistrationNumber = false;

    @Size(max = 100)
    @Column(name = "signing_image_path", length = 100)
    private String signingImagePath;

    @Size(max = 100)
    @Column(name = "student_image_path", length = 100)
    private String studentImagePath;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User user;

    public String getActualRoom() {
        return actualRoom;
    }

    public void setActualRoom(String actualRoom) {
        this.actualRoom = actualRoom;
    }

    public String getActualSeat() {
        return actualSeat;
    }

    public void setActualSeat(String actualSeat) {
        this.actualSeat = actualSeat;
    }

    public String getPlannedRoom() {
        return plannedRoom;
    }

    public void setPlannedRoom(String plannedRoom) {
        this.plannedRoom = plannedRoom;
    }

    public String getPlannedSeat() {
        return plannedSeat;
    }

    public void setPlannedSeat(String plannedSeat) {
        this.plannedSeat = plannedSeat;
    }

    public boolean getDidCheckRegistrationNumber() {
        return didCheckRegistrationNumber;
    }

    public void setDidCheckRegistrationNumber(boolean didCheckRegistrationNumber) {
        this.didCheckRegistrationNumber = didCheckRegistrationNumber;
    }

    public boolean getDidCheckImage() {
        return didCheckImage;
    }

    public void setDidCheckImage(boolean didCheckImage) {
        this.didCheckImage = didCheckImage;
    }

    public boolean getDidCheckName() {
        return didCheckName;
    }

    public void setDidCheckName(boolean didCheckName) {
        this.didCheckName = didCheckName;
    }

    public boolean getDidCheckLogin() {
        return didCheckLogin;
    }

    public void setDidCheckLogin(boolean didCheckLogin) {
        this.didCheckLogin = didCheckLogin;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSigningImagePath() {
        return signingImagePath;
    }

    public void setSigningImagePath(String signingImagePath) {
        this.signingImagePath = signingImagePath;
    }

    public String getStudentImagePath() {
        return studentImagePath;
    }

    public void setStudentImagePath(String studentImagePath) {
        this.studentImagePath = studentImagePath;
    }

    /**
     * Initialisation of the ExamUser on Server start
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (signingImagePath != null && signingImagePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            signingImagePath = signingImagePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
        prevSigningImagePath = signingImagePath; // save current path as old path (needed to know old path in onUpdate() and onDelete())

        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (studentImagePath != null && studentImagePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            studentImagePath = studentImagePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
        prevStudentImagePath = studentImagePath; // save current path as old path (needed to know old path in onUpdate() and onDelete())
    }

    /**
     * Will be called before the entity is persisted (saved).
     * Manages files by taking care of file system changes for this entity.
     */
    @PrePersist
    public void beforeCreate() {
        if (signingImagePath != null) {
            signingImagePath = entityFileService.moveTempFileBeforeEntityPersistence(signingImagePath, FilePathService.getExamUserSignatureFilePath(), false);
        }
        if (studentImagePath != null) {
            studentImagePath = entityFileService.moveTempFileBeforeEntityPersistence(studentImagePath, FilePathService.getStudentImageFilePath(), false);
        }
    }

    /**
     * Will be called after the entity is persisted (saved).
     * Manages files by taking care of file system changes for this entity.
     */
    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (signingImagePath != null && signingImagePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            signingImagePath = signingImagePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }

        if (studentImagePath != null && studentImagePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            studentImagePath = studentImagePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
    }

    /**
     * Will be called before the entity is flushed.
     * Manages files by taking care of file system changes for this entity.
     */
    @PreUpdate
    public void onUpdate() {
        signingImagePath = entityFileService.handlePotentialFileUpdateBeforeEntityPersistence(getId(), prevSigningImagePath, signingImagePath,
                FilePathService.getExamUserSignatureFilePath(), false);
        studentImagePath = entityFileService.handlePotentialFileUpdateBeforeEntityPersistence(getId(), prevStudentImagePath, studentImagePath,
                FilePathService.getStudentImageFilePath(), false);
    }

    /**
     * Will be called after the entity is removed (deleted).
     * Manages files by taking care of file system changes for this entity.
     */
    @PostRemove
    public void onDelete() {
        if (signingImagePath != null) {
            fileService.schedulePathForDeletion(Path.of(prevSigningImagePath), 0);
        }
        if (studentImagePath != null) {
            fileService.schedulePathForDeletion(Path.of(prevStudentImagePath), 0);
        }
    }
}
