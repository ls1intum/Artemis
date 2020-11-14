package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.service.listeners.ComplaintResponseListener;

/**
 * A ComplaintResponse.
 */
@Entity
@EntityListeners(ComplaintResponseListener.class)
@Table(name = "complaint_response")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ComplaintResponse extends DomainObject {

    @Column(name = "response_text")
    @Size(max = 2000)
    private String responseText;

    @Column(name = "submitted_time")
    private ZonedDateTime submittedTime;

    @OneToOne
    @JoinColumn(unique = true)
    private Complaint complaint;

    @ManyToOne
    private User reviewer;

    public String getResponseText() {
        return responseText;
    }

    public ComplaintResponse responseText(String responseText) {
        this.responseText = responseText;
        return this;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public ZonedDateTime getSubmittedTime() {
        return submittedTime;
    }

    public ComplaintResponse submittedTime(ZonedDateTime submittedTime) {
        this.submittedTime = submittedTime;
        return this;
    }

    public void setSubmittedTime(ZonedDateTime submittedTime) {
        this.submittedTime = submittedTime;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public ComplaintResponse complaint(Complaint complaint) {
        this.complaint = complaint;
        return this;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
    }

    public User getReviewer() {
        return reviewer;
    }

    public ComplaintResponse reviewer(User user) {
        this.reviewer = user;
        return this;
    }

    public void setReviewer(User user) {
        this.reviewer = user;
    }

    @Override
    public String toString() {
        return "ComplaintResponse{" + "id=" + getId() + ", responseText='" + getResponseText() + "'" + ", submittedTime='" + getSubmittedTime() + "'" + "}";
    }
}
