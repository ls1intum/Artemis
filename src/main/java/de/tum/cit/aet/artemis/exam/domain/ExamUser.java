package de.tum.cit.aet.artemis.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.ServedFileUrl;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;

@Entity
@Table(name = "exam_user")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamUser extends AbstractAuditingEntity {

    @Column(name = "actual_room")
    private String actualRoom;

    @Column(name = "actual_seat")
    private String actualSeat;

    @Column(name = "planned_room")
    private String plannedRoom;

    @Column(name = "planned_seat")
    private String plannedSeat;

    @JsonIgnore
    @Transient
    private ExamRoom plannedRoomTransient;

    @JsonIgnore
    @Transient
    private ExamSeatDTO plannedSeatTransient;

    @JsonIgnore
    @Transient
    private ExamRoom actualRoomTransient;

    @JsonIgnore
    @Transient
    private ExamSeatDTO actualSeatTransient;

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

    public ExamRoom getPlannedRoomTransient() {
        return plannedRoomTransient;
    }

    public ExamSeatDTO getPlannedSeatTransient() {
        return plannedSeatTransient;
    }

    public void setTransientPlannedRoomAndSeat(ExamRoom plannedRoom, ExamSeatDTO plannedSeat) {
        this.plannedRoomTransient = plannedRoom;
        this.plannedSeatTransient = plannedSeat;
    }

    public ExamRoom getActualRoomTransient() {
        return actualRoomTransient;
    }

    public ExamSeatDTO getActualSeatTransient() {
        return actualSeatTransient;
    }

    public void setTransientActualRoomAndSeat(ExamRoom actualRoom, ExamSeatDTO actualSeat) {
        this.actualRoomTransient = actualRoom;
        this.actualSeatTransient = actualSeat;
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

    /**
     * The path the signature image is served under, relative to {@code api/core/files/}. The column stores only the filename.
     *
     * @return the served path of the signature, or its filename while the exam user has no id yet
     */
    public String getSigningImagePath() {
        return ServedFileUrl.examUserSignature(getId(), signingImagePath);
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename} for why a served URL sent back by a client cannot end up in the column.
     *
     * @param signingImagePath the filename of the signature image, or the URL it is served under
     */
    public void setSigningImagePath(String signingImagePath) {
        this.signingImagePath = FileSystemLocation.storedFilename(signingImagePath);
    }

    /**
     * The path the identification photo is served under, relative to {@code api/core/files/}. The column stores only the filename.
     *
     * @return the served path of the photo, or its filename while the exam user has no id yet
     */
    public String getStudentImagePath() {
        return ServedFileUrl.examUserImage(getId(), studentImagePath);
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename}.
     *
     * @param studentImagePath the filename of the identification photo, or the URL it is served under
     */
    public void setStudentImagePath(String studentImagePath) {
        this.studentImagePath = FileSystemLocation.storedFilename(studentImagePath);
    }

}
