package de.tum.in.www1.artemis.domain;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

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
    private Set<SubmissionVersion> versions = new HashSet<>();

    /**
     * A submission can have multiple results, therefore, results are persisted and removed with a submission.
     * CacheStrategy.NONSTRICT_READ_WRITE leads to problems with the deletion of a submission, because first the results
     * are deleted in a @Transactional method.
     */
    @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties({ "submission", "participation" })
    private List<Result> results = new ArrayList<>();

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

    @JsonView(QuizView.Before.class)
    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    /**
     * Calculates the duration of a submission in minutes and adds it into the json response
     *
     * @return duration in minutes or null if it can not be determined
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getDurationInMinutes() {
        if (this.participation == null || this.participation.getInitializationDate() == null || this.submissionDate == null) {
            return null;
        }

        ZonedDateTime initializationDate = this.participation.getInitializationDate();
        ZonedDateTime submissionDate = this.getSubmissionDate();

        return Duration.between(initializationDate, submissionDate).toMinutes();
    }

    /**
     * Get the latest result of the submission
     *
     * @return a {@link Result} or null
     */
    @Nullable
    @JsonIgnore
    public Result getLatestResult() {
        if (results != null && !results.isEmpty()) {
            return results.get(results.size() - 1);
        }
        return null;
    }

    /**
     * Used to get result by correction round (which ignores automatic results).
     * Works for all exercise types
     *
     * @param correctionRound to get result by
     * @return the result based on the given correction round
     */
    @Nullable
    @JsonIgnore
    public Result getResultForCorrectionRound(int correctionRound) {
        List<Result> filteredResults = filterNonAutomaticResults();
        if (correctionRound >= 0 && filteredResults.size() > correctionRound) {
            return filteredResults.get(correctionRound);
        }
        return null;
    }

    /**
     * @return an unmodifiable list or all non-automatic results
     */
    @NotNull
    private List<Result> filterNonAutomaticResults() {
        return results.stream().filter(result -> result == null || !result.isAutomatic()).toList();
    }

    /**
     * Used to get result by correction round when ignoring all automatic results.
     * The result list can contain null values when it is called here.
     * So accessing the result list by correctionRound either yields null or a result.
     *
     * @param correctionRound for which it is checked if the tutor has a result
     * @return true if the tutor has a result in the correctionRound, false otherwise
     */
    @JsonIgnore
    public boolean hasResultForCorrectionRound(int correctionRound) {
        List<Result> withoutAutomaticResults = filterNonAutomaticResults();
        if (withoutAutomaticResults.size() > correctionRound) {
            return withoutAutomaticResults.get(correctionRound) != null;
        }
        return false;
    }

    /**
     * removes all automatic results from a submissions result list
     * (do not save it like this in the database, as it could remove the automatic results!)
     */
    @JsonIgnore
    public void removeAutomaticResults() {
        this.results = this.results.stream().filter(result -> result == null || !result.isAutomatic()).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * removes all elements from the results list, which are null.
     *
     * This can be used to prepare a submission before sending it to the client. In some cases the submission is loaded from the database
     * with a results list which contains undesired null values. To get rid of them this function can be used.
     *
     * When a submission with results is fetched for a specific assessor, hibernate wants to keep the order of the results list,
     * as it is in the ordered column in the database.
     * To maintain the index of the result with the assessor within the results list, null elements are used as padding.
     */
    @JsonIgnore
    public void removeNullResults() {
        this.results = this.results.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
    }

    @JsonProperty(value = "results", access = JsonProperty.Access.READ_ONLY)
    public List<Result> getResults() {
        return results;
    }

    @JsonIgnore
    public List<Result> getManualResults() {
        return results.stream().filter(result -> result != null && !result.isAutomatic()).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Get the manual result by id of the submission
     * @param resultId id of result
     *
     * @return a {@link Result} or null
     */
    @Nullable
    @JsonIgnore
    public Result getManualResultsById(long resultId) {
        return getManualResults().stream().filter(result1 -> result1.getId().equals(resultId)).findFirst().orElse(null);
    }

    /**
     * Get the first result of the submission
     *
     * @return a {@link Result} or null if no result is present
     */
    @Nullable
    @JsonIgnore
    public Result getFirstResult() {
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    /**
     * Get the first manual result of the submission
     *
     * @return a {@link Result} or null if no result is present
     */
    @Nullable
    @JsonIgnore
    public Result getFirstManualResult() {
        if (results != null && !results.isEmpty()) {
            return this.getManualResults().get(0);
        }
        return null;
    }

    /**
     * Add a result to the list.
     * NOTE: You must make sure to correctly persist the result in the database!
     *
     * @param result the {@link Result} which should be added
     */
    public void addResult(Result result) {
        this.results.add(result);
    }

    /**
     * Set the results list to the specified list.
     * NOTE: You must correctly persist this change in the database manually!
     *
     * @param results The list of {@link Result} which should replace the existing results of the submission
     */
    @JsonProperty(value = "results", access = JsonProperty.Access.WRITE_ONLY)
    public void setResults(List<Result> results) {
        this.results = results;
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

    /**
     * determine whether a submission is empty, i.e. the student did not work properly on the corresponding exercise
     * @return whether the submission is empty (true) or not (false)
     */
    public abstract boolean isEmpty();

    /**
     * In case user calls for correctionRound 0, but more manual results already exists
     * and he has not requested a specific result, remove any other results
     *
     * @param correctionRound for which not to remove results
     * @param resultId specific resultId
     */
    public void removeNotNeededResults(int correctionRound, Long resultId) {
        if (correctionRound == 0 && resultId == null && getResults().size() >= 2) {
            var resultList = new ArrayList<Result>();
            resultList.add(getFirstManualResult());
            setResults(resultList);
        }
    }

    /**
     * Returns the result of a submission which has a complaint
     * @return the result which has a complaint or null if there is no result which has a complaint
     */
    @Nullable
    @JsonIgnore
    public Result getResultWithComplaint() {
        return results.stream().filter(result -> Boolean.TRUE.equals(result.hasComplaint())).findFirst().orElse(null);
    }
}
