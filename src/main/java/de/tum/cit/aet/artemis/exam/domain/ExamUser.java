package de.tum.cit.aet.artemis.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;

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

}
