package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.COMPLAINT_TEXT_LIMIT;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.Participant;

/**
 * A Complaint.
 */
@Entity
@Table(name = "complaint")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Complaint extends DomainObject {

    @Column(name = "complaint_text", columnDefinition = "TEXT")
    @Size(max = COMPLAINT_TEXT_LIMIT)
    private String complaintText;

    @Column(name = "accepted")
    private Boolean accepted;

    @Column(name = "submitted_time")
    private ZonedDateTime submittedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "complaint_type")
    private ComplaintType complaintType;

    // TODO: delete in the next major release
    @Deprecated
    @Column(name = "result_before_complaint")
    // @Lob
    @JsonIgnore
    private String resultBeforeComplaint;

    @OneToOne(mappedBy = "complaint")
    @JsonIgnoreProperties(value = "complaint", allowGetters = true)
    private ComplaintResponse complaintResponse;

    @OneToOne
    @JoinColumn(unique = true)
    private Result result;

    @ManyToOne
    private User student;

    @ManyToOne
    private Team team;

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

    public void setSubmittedTime(ZonedDateTime submittedTime) {
        this.submittedTime = submittedTime;
    }

    public ComplaintType getComplaintType() {
        return complaintType;
    }

    public Complaint complaintType(ComplaintType complaintType) {
        this.complaintType = complaintType;
        return this;
    }

    public void setComplaintType(ComplaintType complaintType) {
        this.complaintType = complaintType;
    }

    public Result getResult() {
        return result;
    }

    public Complaint result(Result result) {
        this.result = result;
        return this;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public User getStudent() {
        return student;
    }

    public Team getTeam() {
        return team;
    }

    @JsonIgnore
    public Participant getParticipant() {
        return Optional.ofNullable((Participant) student).orElse(team);
    }

    public Complaint participant(Participant participant) {
        setParticipant(participant);
        return this;
    }

    public ComplaintResponse getComplaintResponse() {
        return complaintResponse;
    }

    public void setComplaintResponse(ComplaintResponse complaintResponse) {
        this.complaintResponse = complaintResponse;
    }

    /**
     * allows to set the participant independent whether it is a team or user
     * @param participant either a team or user
     */
    public void setParticipant(Participant participant) {
        if (participant instanceof User) {
            this.student = (User) participant;
        }
        else if (participant instanceof Team) {
            this.team = (Team) participant;
        }
        else if (participant == null) {
            this.student = null;
            this.team = null;
        }
        else {
            throw new Error("Unknown participant type");
        }
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /**
     * Removes the participant from the complaint, can be invoked to make sure that sensitive information is not sent to the client. E.g. tutors should not see information about
     * the participant.
     */
    public void filterSensitiveInformation() {
        setParticipant(null);
    }

    /**
     * Filters out the reviewer, if the user was not the reviewer
     * @param user - the user for which the reviewer should not be deleted
     */
    public void filterForeignReviewer(User user) {
        User assessor = result.getAssessor();
        if (!assessor.equals(user)) {
            result.setAssessor(null);
        }
    }

    @Override
    public String toString() {
        return "Complaint{" + "id=" + getId() + ", complaintText='" + getComplaintText() + "'" + ", accepted='" + isAccepted() + "'" + ", submittedTime='" + getSubmittedTime()
                + "'}";
    }
}
