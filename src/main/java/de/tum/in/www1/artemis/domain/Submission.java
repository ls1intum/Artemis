package de.tum.in.www1.artemis.domain;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A Submission.
 */
@Entity
@Table(name = "submission")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
// Annotation necessary to distinguish between concrete implementations of Submission when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = ProgrammingSubmission.class, name = "programming"), @JsonSubTypes.Type(value = ModelingSubmission.class, name = "modeling"),
        @JsonSubTypes.Type(value = QuizSubmission.class, name = "quiz"), @JsonSubTypes.Type(value = TextSubmission.class, name = "text"),
        @JsonSubTypes.Type(value = FileUploadSubmission.class, name = "file-upload"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Submission extends DomainObject {

    @Column(name = "submitted")
    @JsonView(QuizView.Before.class)
    private Boolean submitted;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    @JsonView(QuizView.Before.class)
    private SubmissionType type;

    @Column(name = "example_submission")
    private Boolean exampleSubmission;

    @ManyToOne
    private Participation participation;

    @JsonIgnore
    @OneToMany(mappedBy = "submission", cascade = CascadeType.REMOVE)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    // TODO use Set here instead of List
    private List<SubmissionVersion> versions = new ArrayList<>();

    /**
     * A submission can have a result and therefore, results are persisted and removed with a submission.
     */
    @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIgnoreProperties({ "submission", "participation" })
    @OrderColumn
    private List<Result> results = new ArrayList<>();

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

    @JsonView(QuizView.Before.class)
    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long durationInMinutes;

    /**
     * Calculates the duration of a submission in minutes
     *
     * @return duration in minutes or null if it can not be determined
     */
    public Long getDurationInMinutes() {
        if (this.participation == null || this.participation.getInitializationDate() == null || this.submissionDate == null) {
            return null;
        }

        ZonedDateTime initilizationDate = this.participation.getInitializationDate();
        ZonedDateTime submissionDate = this.getSubmissionDate();

        return Duration.between(initilizationDate, submissionDate).toMinutes();
    }

    // TODO: double check Jackson and client compatibility, maybe refactoring to getLatestResult
    @Nullable
    public Result getResult() {
        // in all cases (except 2nd, 3rd correction, etc.) we would like to have the latest result
        // getLatestResult
        if (!results.isEmpty()) {
            return results.get(results.size() - 1);
        }
        return null;
    }

    // TODO: refactoring addResult
    @Nullable
    public Result getFirstResult() {
        // getLatestResult
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    public void setResult(Result result) {
        // addResult
        this.results.add(result);
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public Submission submissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
        return this;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Boolean isSubmitted() {
        return submitted != null ? submitted : false;
    }

    public Submission submitted(Boolean submitted) {
        this.submitted = submitted;
        return this;
    }

    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    public SubmissionType getType() {
        return type;
    }

    public Submission type(SubmissionType type) {
        this.type = type;
        return this;
    }

    public void setType(SubmissionType type) {
        this.type = type;
    }

    public Boolean isExampleSubmission() {
        return exampleSubmission;
    }

    public void setExampleSubmission(Boolean exampleSubmission) {
        this.exampleSubmission = exampleSubmission;
    }
}
