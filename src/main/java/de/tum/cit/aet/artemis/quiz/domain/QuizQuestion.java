package de.tum.cit.aet.artemis.quiz.domain;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.enumeration.ScoringType;
import de.tum.cit.aet.artemis.quiz.config.QuizView;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;

/**
 * A QuizQuestion.
 */
@Entity
@Table(name = "quiz_question")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "Q")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceQuestion.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropQuestion.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerQuestion.class, name = "short-answer") }
)
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizQuestion extends DomainObject {

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "text", length = 1000)
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "hint")
    @JsonView(QuizView.Before.class)
    private String hint;

    @Column(name = "explanation", length = 500)
    @JsonView(QuizView.After.class)
    private String explanation;

    @Column(name = "points")
    @JsonView(QuizView.Before.class)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_type")
    @JsonView(QuizView.Before.class)
    private ScoringType scoringType;

    @Column(name = "randomize_order")
    @JsonView(QuizView.Before.class)
    private Boolean randomizeOrder;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @Column(name = "quiz_group_id")
    @JsonView(QuizView.Before.class)
    private Long quizGroupId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(unique = true)
    private QuizQuestionStatistic quizQuestionStatistic;

    @ManyToOne
    @JsonIgnore
    private QuizExercise exercise;

    @Transient
    private QuizGroup quizGroup;

    public String getTitle() {
        return title;
    }

    public QuizQuestion title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public QuizQuestion text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getPoints() {
        return points;
    }

    public QuizQuestion score(Integer score) {
        this.points = score;
        return this;
    }

    public void setPoints(Integer score) {
        this.points = score;
    }

    public ScoringType getScoringType() {
        return scoringType;
    }

    public void setScoringType(ScoringType scoringType) {
        this.scoringType = scoringType;
    }

    public Boolean isRandomizeOrder() {
        return randomizeOrder;
    }

    public void setRandomizeOrder(Boolean randomizeOrder) {
        this.randomizeOrder = randomizeOrder;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public QuizQuestionStatistic getQuizQuestionStatistic() {
        return quizQuestionStatistic;
    }

    public void setQuizQuestionStatistic(QuizQuestionStatistic quizQuestionStatistic) {
        this.quizQuestionStatistic = quizQuestionStatistic;
    }

    public QuizExercise getExercise() {
        return exercise;
    }

    public void setExercise(QuizExercise quizExercise) {
        this.exercise = quizExercise;
    }

    public Long getQuizGroupId() {
        return quizGroupId;
    }

    public void setQuizGroupId(Long quizGroupId) {
        this.quizGroupId = quizGroupId;
    }

    @JsonProperty(value = "quizGroup", access = JsonProperty.Access.READ_ONLY)
    public QuizGroup getQuizGroup() {
        return quizGroup;
    }

    @JsonProperty(value = "quizGroup", access = JsonProperty.Access.WRITE_ONLY)
    public void setQuizGroup(QuizGroup quizGroup) {
        this.quizGroup = quizGroup;
    }

    /**
     * Calculate the score for the given answer
     *
     * @param submittedAnswer The answer given for this question
     * @return the resulting score
     */
    public double scoreForAnswer(SubmittedAnswer submittedAnswer) {
        return makeScoringStrategy().calculateScore(this, submittedAnswer);
    }

    /**
     * Checks if the given answer is 100 % correct. This is independent of the scoring type
     *
     * @param submittedAnswer The answer given for this question
     * @return true, if the answer is 100% correct, false otherwise
     */
    public boolean isAnswerCorrect(SubmittedAnswer submittedAnswer) {
        return makeScoringStrategy().calculateScore(this, submittedAnswer) == getPoints();
    }

    protected abstract ScoringStrategy makeScoringStrategy();

    /**
     * filter out information about correct answers
     */
    public void filterForStudentsDuringQuiz() {
        setExplanation(null);
        setQuizQuestionStatistic(null);
    }

    /**
     * filter out information about correct answers
     */
    public void filterForStatisticWebsocket() {
        setExplanation(null);
    }

    /**
     * Check if the question is valid. This means the question has a title and fulfills any additional requirements by the specific subclass
     *
     * @return true, if the question is valid, otherwise false
     */
    @JsonIgnore
    public Boolean isValid() {
        // check title and score
        return getTitle() != null && !getTitle().isEmpty() && getPoints() >= 1;
    }

    /**
     * NOTE: do not use this in a transactional context and do not save the returned object to the database
     * This method is useful when we want to cut off attributes while sending entities to the client and we are only interested in the id of the object
     * We use polymorphism here, so subclasses should implement / override this method to create the correct object type
     *
     * @return an empty question just including the id of the object
     */
    public abstract QuizQuestion copyQuestionId();

    /**
     * undo all changes which are not allowed
     *
     * @param originalQuizQuestion the original not changed QuizQuestion, to detect the changes
     */
    public abstract void undoUnallowedChanges(QuizQuestion originalQuizQuestion);

    /**
     * check if an update of the Results and Statistics is necessary
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     * @return a boolean which is true if the question-changes make an update necessary and false if not
     */
    public abstract boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion);

    /**
     * Initialize QuizQuestionStatistic of the implementor
     *
     */
    public abstract void initializeStatistic();
}
