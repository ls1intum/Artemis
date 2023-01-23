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

    @Column(name = "student_identifier")
    private String studentIdentifier;

    @Column(name = "matriculation_number")
    private String matriculationNumber;

    @Column(name = "lecture_hall")
    private String lectureHall;

    @Column(name = "seat")
    private String seat;

    @Column(name = "did_check_image")
    private Boolean didCheckImage;

    @Column(name = "did_check_name")
    private Boolean didCheckName;

    @Column(name = "did_check_artemis")
    private Boolean didCheckArtemis;

    @Size(max = 256)
    @Column(name = "signing_url", length = 256)
    private String signingUrl;

    // todo: add a column for the signature as json string.
    // var signingDrawing: PKDrawing?

    @ManyToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User user;

    public String getStudentIdentifier() {
        return studentIdentifier;
    }

    public void setStudentIdentifier(String studentIdentifier) {
        this.studentIdentifier = studentIdentifier;
    }

    public String getMatriculationNumber() {
        return matriculationNumber;
    }

    public void setMatriculationNumber(String matriculationNumber) {
        this.matriculationNumber = matriculationNumber;
    }

    public String getLectureHall() {
        return lectureHall;
    }

    public void setLectureHall(String lectureHall) {
        this.lectureHall = lectureHall;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
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

    public Boolean getDidCheckArtemis() {
        return didCheckArtemis;
    }

    public void setDidCheckArtemis(Boolean didCheckArtemis) {
        this.didCheckArtemis = didCheckArtemis;
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

    public String getSigningUrl() {
        return signingUrl;
    }

    public void setSigningUrl(String signingUrl) {
        this.signingUrl = signingUrl;
    }
}
