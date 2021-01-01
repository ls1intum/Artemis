package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A ComplaintResponse.
 */
@Entity
@Table(name = "complaint_response")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ComplaintResponse extends DomainObject {

    @Column(name = "response_text")
    @Size(max = 2000)
    private String responseText;

    /**
     * will be set as soon as the reviewer has submitted his complaint evaluation
     */
    @Column(name = "submitted_time")
    private ZonedDateTime submittedTime;

    /**
     * will be set as soon as the evaluation of a complaint by a reviewer begins (possibly null for old complaint responses befor this column was added)
     */
    // ToDo add created time to liquibase (currently manually set via sql)
    @Column(name = "created_time")
    private ZonedDateTime createdTime;

    @Transient
    private boolean isCurrentlyLocked;

    @OneToOne
    @JoinColumn(unique = true)
    @JsonIgnoreProperties(value = "complaintResponse", allowSetters = true)
    private Complaint complaint;

    @ManyToOne
    private User reviewer;

    /**
     * Calculates if the complaint response is still time locked or not
     * @return true if complaint response is time locked, false otherwise
     */
    @JsonProperty("isCurrentlyLocked")
    public boolean isCurrentlyLocked() {
        if (createdTime == null) {
            return false;
        }

        // ToDo load lock time for complaints from yaml file
        ZonedDateTime lockedUntil = createdTime.plusHours(7);

        return ZonedDateTime.now().isBefore(lockedUntil);
    }

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

    public ZonedDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(ZonedDateTime createdTime) {
        this.createdTime = createdTime;
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
