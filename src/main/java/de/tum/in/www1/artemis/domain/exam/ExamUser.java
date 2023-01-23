package de.tum.in.www1.artemis.domain.exam;

import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.User;

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

    @Column(name = "did_check_image")
    private Boolean didCheckImage;

    @Column(name = "did_check_name")
    private Boolean didCheckName;

    @Column(name = "did_check_login")
    private Boolean didCheckLogin;

    @Column(name = "did_check_registration_number")
    private Boolean didCheckRegistrationNumber;

    @Size(max = 100)
    @Column(name = "signing_image_path", length = 100)
    private String signingImagePath;

    // todo: add a column for the signature as json string.
    // var signingDrawing: PKDrawing?

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

    public Boolean getDidCheckRegistrationNumber() {
        return didCheckRegistrationNumber;
    }

    public void setDidCheckRegistrationNumber(Boolean didCheckRegistrationNumber) {
        this.didCheckRegistrationNumber = didCheckRegistrationNumber;
    }

    public Boolean getDidCheckImage() {
        return didCheckImage;
    }

    public void setDidCheckImage(Boolean didCheckImage) {
        this.didCheckImage = didCheckImage;
    }

    public Boolean getDidCheckName() {
        return didCheckName;
    }

    public void setDidCheckName(Boolean didCheckName) {
        this.didCheckName = didCheckName;
    }

    public Boolean getDidCheckLogin() {
        return didCheckLogin;
    }

    public void setDidCheckLogin(Boolean didCheckLogin) {
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
}
