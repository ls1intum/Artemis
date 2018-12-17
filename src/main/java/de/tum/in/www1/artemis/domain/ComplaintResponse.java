package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.Size;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A ComplaintResponse.
 */
@Entity
@Table(name = "complaint_response")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ComplaintResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "response_text")
    @Size(max = 2000)
    private String responseText;

    @Column(name = "submitted_time")
    private ZonedDateTime submittedTime;

    @OneToOne
    @JoinColumn(unique = true)
    private Complaint complaint;

    @OneToOne
    @JoinColumn(unique = true)
    private User reviewer;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ComplaintResponse complaintResponse = (ComplaintResponse) o;
        if (complaintResponse.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), complaintResponse.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ComplaintResponse{" +
            "id=" + getId() +
            ", responseText='" + getResponseText() + "'" +
            ", submittedTime='" + getSubmittedTime() + "'" +
            "}";
    }
}
