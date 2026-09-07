package de.tum.cit.aet.artemis.exercise.domain;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.ConcreteProxy;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * A Submission.
 */
@Entity
@Table(name = "submission")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@ConcreteProxy
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
// Annotation necessary to distinguish between concrete implementations of Submission when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProgrammingSubmission.class, name = "programming"),
    @JsonSubTypes.Type(value = ModelingSubmission.class, name = "modeling"),
    @JsonSubTypes.Type(value = QuizSubmission.class, name = "quiz"),
    @JsonSubTypes.Type(value = TextSubmission.class, name = "text"),
    @JsonSubTypes.Type(value = FileUploadSubmission.class, name = "file-upload")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Submission extends DomainObject implements Comparable<Submission> {

    @Column(name = "submitted")
    private Boolean submitted;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private SubmissionType type;

    @Column(name = "example_submission")
    private Boolean exampleSubmission;

    @ManyToOne
    private Participation participation;

    @JsonIgnore
    @OneToMany(mappedBy = "submission", cascade = CascadeType.REMOVE)
    private Set<SubmissionVersion> versions = new HashSet<>();

    /**
     * Orders results by their id, putting a result that was not saved yet at the end: it has just been created, so it
     * is the newest one. Without the null handling every lookup here would throw for an in-memory result.
     */
    private static final Comparator<Result> BY_ID = Comparator.comparing(Result::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * A submission can have multiple results, therefore, results are persisted and removed with a submission.
     * <p>
     * A set, not a list: this used to be an ordered list whose position carried the correction round, which meant every
     * write in this area had to load and re-save the whole submission so that Hibernate could renumber the order
     * column. The correction round now lives on {@link Result#getCorrectionRound()}, so nothing needs the position.
     * <p>
     * Ordered by id all the same. Nothing on the server depends on it, but the collection is serialized to the client,
     * and a hash set would hand it the results in an order that can change between two requests for the same data.
     */
    @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @JsonIgnoreProperties({ "submission", "participation" })
    private Set<Result> results = new LinkedHashSet<>();

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

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
        Result latestResult = Optional.ofNullable(results).orElse(Set.of()).stream().filter(Objects::nonNull).max(BY_ID).orElse(null);

        if (latestResult != null) {
            latestResult.setSubmission(this);
        }

        return latestResult;
    }

    /**
     * Returns the most recent result that has been completed, i.e. has a non-null completion date.
     * This ignores draft results that might exist when an assessment lock was created but not
     * finished.
     *
     * @return the latest completed {@link Result} or {@code null} if none exists
     */
    @Nullable
    @JsonIgnore
    public Result getLatestCompletedResult() {
        Result latestResult = Optional.ofNullable(results).orElse(Set.of()).stream().filter(result -> result != null && result.getCompletionDate() != null)
                .max(Comparator.comparing(Result::getCompletionDate).thenComparing(BY_ID)).orElse(null);

        if (latestResult != null) {
            latestResult.setSubmission(this);
        }

        return latestResult;
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
        if (correctionRound < 0) {
            return null;
        }
        // The lowest id among the matches, so that the answer does not depend on the iteration order of an unordered
        // set. There should only ever be one result per round, and picking the earliest is what the ordered list did.
        return getManualResults().stream().filter(result -> Objects.equals(result.getCorrectionRound(), correctionRound)).min(BY_ID).orElse(null);
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
        return getResultForCorrectionRound(correctionRound) != null;
    }

    /**
     * removes all automatic results from a submissions result list
     * (do not save it like this in the database, as it could remove the automatic results!)
     */
    @JsonIgnore
    public void removeAutomaticResults() {
        this.results = this.results.stream().filter(result -> result == null || !(result.isAutomatic() || result.isAthenaBased()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * removes all elements from the results list, which are null.
     * <p>
     * This can be used to prepare a submission before sending it to the client. In some cases the submission is loaded from the database
     * with a results list which contains undesired null values. To get rid of them this function can be used.
     * <p>
     * When a submission with results is fetched for a specific assessor, hibernate wants to keep the order of the results list,
     * as it is in the ordered column in the database.
     * To maintain the index of the result with the assessor within the results list, null elements are used as padding.
     */
    @JsonIgnore
    public void removeNullResults() {
        this.results = this.results.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @JsonProperty(value = "results", access = JsonProperty.Access.READ_ONLY)
    public Set<Result> getResults() {
        return results;
    }

    @JsonIgnore
    public Set<Result> getAutomaticResults() {
        return results.stream().filter(result -> result != null && (result.isAutomatic() || result.isAthenaBased())).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @JsonIgnore
    public Set<Result> getManualResults() {
        return results.stream().filter(result -> result != null && !result.isAutomatic() && !result.isAthenaBased()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * This method is necessary to ignore Athena results in the assessment view
     *
     * @return non athena automatic results excluding null results
     */
    @JsonIgnore
    public Set<Result> getNonAthenaResults() {
        return results.stream().filter(result -> result != null && !result.isAthenaBased()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get the manual result by id of the submission
     *
     * @param resultId id of result
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
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.stream().filter(Objects::nonNull).min(BY_ID).orElse(null);
    }

    /**
     * Get the first manual result of the submission
     *
     * @return a {@link Result} or null if no result is present
     */
    @Nullable
    @JsonIgnore
    public Result getFirstManualResult() {
        // The earliest manual result, which is the one of the first correction round. Guard on the manual results, not
        // on all results: a submission can carry only automatic or Athena results and then there is none.
        if (results == null) {
            return null;
        }
        return getManualResults().stream().min(BY_ID).orElse(null);
    }

    /**
     * Get the last manual result of the submission, i.e. the newest correction round.
     * <p>
     * Deliberately different from {@link #getLatestResult()}, which returns the result with the highest id and therefore
     * can return an automatic or Athena result. Operations that act on what a tutor is assessing have to use this one,
     * because automatic results are not correction rounds and never carry an assessor.
     *
     * @return a {@link Result} or null if the submission has no manual result
     */
    @Nullable
    @JsonIgnore
    public Result getLatestManualResult() {
        // The most recent manual result, which is the one of the highest correction round.
        if (results == null) {
            return null;
        }
        return getManualResults().stream().max(BY_ID).orElse(null);
    }

    /**
     * Adds a result to this submission and, if it is a correction-round result that does not have a round yet, assigns
     * the next one.
     * <p>
     * This is where the round used to come from implicitly: the results were an ordered list and the position carried
     * the round, so adding a result to the list decided which round it belonged to. The round now lives on the result,
     * and this is the same moment, so the behaviour is unchanged for every caller that does not set it itself.
     * {@code SubmissionService.lockSubmission} does set it, from the round the tutor asked for, and that takes
     * precedence. Automatic and Athena results are not correction rounds and keep no round.
     *
     * @param result the result to add
     */
    public void addResult(Result result) {
        if (result != null) {
            if (result.getCorrectionRound() == null && !result.isAutomatic() && !result.isAthenaBased()) {
                result.setCorrectionRound(countCorrectionRoundResults(result));
            }
            // Keep both ends of the association in sync. The results are mapped on the inverse side and cascade, so
            // without this Hibernate inserts the cascaded result with an empty submission_id and only fills it in with
            // a follow-up update, which a not-null constraint on the column rightly rejects.
            result.setSubmission(this);
        }
        this.results.add(result);
    }

    /**
     * @param resultToAdd the result that is about to be added, which must not count itself
     * @return how many correction-round results this submission already holds
     */
    private int countCorrectionRoundResults(Result resultToAdd) {
        return (int) results.stream().filter(other -> other != null && other != resultToAdd && !other.isAutomatic() && !other.isAthenaBased()).count();
    }

    /**
     * Set the results list to the specified list.
     * NOTE: You must correctly persist this change in the database manually!
     *
     * @param results The list of {@link Result} which should replace the existing results of the submission
     */
    @JsonProperty(value = "results", access = JsonProperty.Access.WRITE_ONLY)
    public void setResults(Set<Result> results) {
        this.results = results != null ? results : new LinkedHashSet<>();
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
     *
     * @return whether the submission is empty (true) or not (false)
     */
    public abstract boolean isEmpty();

    /**
     * used to distinguish the type when used in collections or DTOs
     *
     * @return the exercise type (e.g. programming, text)
     */
    public abstract String getSubmissionExerciseType();

    /**
     * In case user calls for correctionRound 0, but more manual results already exists
     * and they have not requested a specific result, remove any other results
     *
     * @param correctionRound for which not to remove results
     * @param resultId        specific resultId
     */
    public void removeNotNeededResults(int correctionRound, Long resultId) {
        if (correctionRound == 0 && resultId == null && getResults().size() >= 2) {
            var remainingResults = new HashSet<Result>();
            var firstManualResult = getFirstManualResult();
            if (firstManualResult != null) {
                remainingResults.add(firstManualResult);
            }
            setResults(remainingResults);
        }
    }

    /**
     * Returns the result of a submission which has a complaint
     *
     * @return the result which has a complaint or null if there is no result which has a complaint
     */
    @Nullable
    @JsonIgnore
    public Result getResultWithComplaint() {
        return results.stream().filter(result -> Boolean.TRUE.equals(result.hasComplaint())).findFirst().orElse(null);
    }

    @Override
    public int compareTo(Submission other) {
        if (getSubmissionDate() == null || other.getSubmissionDate() == null || Objects.equals(getSubmissionDate(), other.getSubmissionDate())) {
            // this case should not happen, but in the rare case we can compare the ids (in tests, the submission dates might be identical as ms are not stored in the database)
            // newer ids are typically later
            return getId().compareTo(other.getId());
        }
        return getSubmissionDate().compareTo(other.getSubmissionDate());
    }
}
