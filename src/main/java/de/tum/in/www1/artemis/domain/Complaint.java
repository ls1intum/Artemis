package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.Size;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A Complaint.
 */
@Entity
@Table(name = "complaint")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Complaint implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_text")
    @Size(max = 2000)
    private String complaintText;

    @Column(name = "accepted")
    private Boolean accepted;

    @Column(name = "submitted_time")
    private ZonedDateTime submittedTime;

    @Column(name = "result_before_complaint")
    @Lob
    private String resultBeforeComplaint;

    @OneToOne
    @JoinColumn(unique = true)
    private Result result;

    @OneToOne
    @JoinColumn(unique = true)
    private User student;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComplaintText() {
        return complaintText;
    }

    public Complaint complaintText(String complaintText) {
        this.complaintText = complaintText;
        return this;
    }

    public void setComplaintText(String complaintText) {
        this.complaintText = complaintText;
    }

    public Boolean isAccepted() {
        return accepted;
    }

    public Complaint accepted(Boolean accepted) {
        this.accepted = accepted;
        return this;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public ZonedDateTime getSubmittedTime() {
        return submittedTime;
    }

    public Complaint submittedTime(ZonedDateTime submittedTime) {
        this.submittedTime = submittedTime;
        return this;
    }

    public void setSubmittedTime(ZonedDateTime submittedTime) {
        this.submittedTime = submittedTime;
    }

    public String getResultBeforeComplaint() {
        return resultBeforeComplaint;
    }

    public Complaint resultBeforeComplaint(String resultBeforeComplaint) {
        this.resultBeforeComplaint = resultBeforeComplaint;
        return this;
    }

    public void setResultBeforeComplaint(String resultBeforeComplaint) {
        this.resultBeforeComplaint = resultBeforeComplaint;
    }

    public Result getResult() {
        return result;
    }

    public Complaint result(Result Result) {
        this.result = Result;
        return this;
    }

    public void setResult(Result Result) {
        this.result = Result;
    }

    public User getStudent() {
        return student;
    }

    public Complaint student(User user) {
        this.student = user;
        return this;
    }

    public void setStudent(User user) {
        this.student = user;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Complaint complaint = (Complaint) o;
        if (complaint.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), complaint.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Complaint{" +
            "id=" + getId() +
            ", complaintText='" + getComplaintText() + "'" +
            ", accepted='" + isAccepted() + "'" +
            ", submittedTime='" + getSubmittedTime() + "'" +
            ", resultBeforeComplaint='" + getResultBeforeComplaint() + "'" +
            "}";
    }
}
