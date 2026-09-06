package de.tum.cit.aet.artemis.assessment.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_TEXT_LIMIT;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

/**
 * A Complaint.
 */
@Entity
@Table(name = "complaint")
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
    @Column(name = "complaint_type", nullable = false)
    private ComplaintType complaintType = ComplaintType.COMPLAINT;      // default value

    @OneToOne(mappedBy = "complaint")
    @JsonIgnoreProperties(value = "complaint", allowGetters = true)
    private ComplaintResponse complaintResponse;

    @OneToOne
    @JoinColumn(unique = true, nullable = false)
    private Result result;

    /**
     * The exercise the complained-about result belongs to, denormalized from {@link Result#getExerciseId()}.
     * <p>
     * The complaint counts on the course and exam assessment dashboards filter a set of exercise ids. Reaching the
     * exercise through {@code result} made them scan every complaint in the system: on production, one course
     * dashboard examined all 25,646 complaints to find its 43. This column is kept in sync by
     * {@link #setResult(Result)} and {@link #result(Result)}, the only ways a result is attached to a complaint.
     */
    @Column(name = "exercise_id", nullable = false)
    private long exerciseId;

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
        setResult(result);
        return this;
    }

    public void setResult(Result result) {
        this.result = result;
        if (result != null) {
            this.exerciseId = result.getExerciseId();
        }
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
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
     *
     * @param participant either a team or user
     */
    public void setParticipant(Participant participant) {
        switch (participant) {
            case User user -> this.student = user;
            case Team team -> this.team = team;
            case null -> {
                this.student = null;
                this.team = null;
            }
            default -> throw new Error("Unknown participant type");
        }
    }

    /**
     * Removes the participant from the complaint, can be invoked to make sure that sensitive information is not sent to the client. E.g. tutors should not see information about
     * the participant.
     */
    public void filterSensitiveInformation() {
        setParticipant(null);
    }

    /**
     * Filters out the reviewer, if the user was not the reviewer
     *
     * @param user - the user for which the reviewer should not be deleted
     */
    public void filterForeignReviewer(User user) {
        User assessor = result.getAssessor();
        if (!user.equals(assessor)) {
            result.filterSensitiveInformation();
        }
    }

    @Override
    public String toString() {
        return "Complaint{" + "id=" + getId() + ", complaintText='" + getComplaintText() + "'" + ", accepted='" + isAccepted() + "'" + ", submittedTime='" + getSubmittedTime()
                + "'}";
    }
}
