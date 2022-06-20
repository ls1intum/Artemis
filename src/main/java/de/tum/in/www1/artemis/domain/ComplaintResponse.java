package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.COMPLAINT_LOCK_DURATION_IN_MINUTES;
import static de.tum.in.www1.artemis.config.Constants.COMPLAINT_RESPONSE_TEXT_LIMIT;

import java.time.ZoneOffset;
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
 *
 *  The createdDate in {@link AbstractAuditingEntity#createdDate} has a special meaning in this entity as it is used to calculate the lock status. See also
 *  {@link ComplaintResponse#isCurrentlyLocked()}
 */
@Entity
@Table(name = "complaint_response")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ComplaintResponse extends AbstractAuditingEntity {

    @Column(name = "response_text", columnDefinition = "TEXT")
    @Size(max = COMPLAINT_RESPONSE_TEXT_LIMIT)
    private String responseText;

    /**
     * will be set as soon as the reviewer has submitted his complaint evaluation
     */
    @Column(name = "submitted_time")
    private ZonedDateTime submittedTime;

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
        if (getCreatedDate() == null) {
            return false;
        }
        ZonedDateTime createdDateInUTC = ZonedDateTime.ofInstant(getCreatedDate(), ZoneOffset.UTC);

        ZonedDateTime lockedUntil = createdDateInUTC.plusMinutes(COMPLAINT_LOCK_DURATION_IN_MINUTES);

        return ZonedDateTime.now(ZoneOffset.UTC).isBefore(lockedUntil);
    }

    /**
     * Provides the client with information about the date when the lock is ending
     * @return date of lock end in UTC
     */
    @JsonProperty("lockEndDate")
    public ZonedDateTime lockEndDate() {
        if (getCreatedDate() == null) {
            return null;
        }
        ZonedDateTime createdDateInUTC = ZonedDateTime.ofInstant(getCreatedDate(), ZoneOffset.UTC);
        return createdDateInUTC.plusMinutes(COMPLAINT_LOCK_DURATION_IN_MINUTES);
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
