package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A Self Learning Feedback Request.
 */
@Entity
@Table(name = "self_learning_feedback_request")
@EntityListeners({ AuditingEntityListener.class })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SelfLearningFeedbackRequest extends DomainObject {

    @Column(name = "request_datetime")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime requestDateTime;

    @Column(name = "response_datetime")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime responseDateTime;

    @Column(name = "successful")
    @JsonView(QuizView.Before.class)
    private Boolean successful;

    @OneToOne
    @JsonIgnoreProperties({ "submission", "participation" })
    @JsonIncludeProperties({ "participation.id" })
    private Result result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({ "results" })
    private Submission submission;

    public ZonedDateTime getRequestDateTime() {
        return requestDateTime;
    }

    public void setRequestDateTime(ZonedDateTime requestDateTime) {
        this.requestDateTime = requestDateTime;
    }

    public ZonedDateTime getResponseDateTime() {
        return responseDateTime;
    }

    public void setResponseDateTime(ZonedDateTime responseDateTime) {
        this.responseDateTime = responseDateTime;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    @Transient
    @JsonProperty("type")
    public String getType() {
        return this.getClass().getSimpleName();
    }
}
