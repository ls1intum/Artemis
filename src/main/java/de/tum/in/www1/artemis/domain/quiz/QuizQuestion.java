package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.SubmittedAnswer;
import de.tum.in.www1.artemis.domain.enumeration.ScoringType;
import de.tum.in.www1.artemis.domain.quiz.scoring.ScoringStrategyFactory;
import de.tum.in.www1.artemis.domain.view.QuizView;

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
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestion.class, name = "multiple-choice"), @JsonSubTypes.Type(value = DragAndDropQuestion.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestion.class, name = "short-answer") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "text", length = 1000)
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "hint")
    @JsonView(QuizView.Before.class)
    private String hint;

    @Column(name = "explanation")
    @JsonView(QuizView.After.class)
    private String explanation;

    @Column(name = "score")
    @JsonView(QuizView.Before.class)
    private Integer score;

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

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(unique = true)
    @JsonView(QuizView.After.class)
    private QuizQuestionStatistic quizQuestionStatistic;

    @ManyToOne
    @JsonIgnore
    private QuizExercise exercise;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public QuizQuestion hint(String hint) {
        this.hint = hint;
        return this;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public QuizQuestion explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getScore() {
        return score;
    }

    public QuizQuestion score(Integer score) {
        this.score = score;
        return this;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public ScoringType getScoringType() {
        return scoringType;
    }

    public QuizQuestion scoringType(ScoringType scoringType) {
        this.scoringType = scoringType;
        return this;
    }

    public void setScoringType(ScoringType scoringType) {
        this.scoringType = scoringType;
    }

    public Boolean isRandomizeOrder() {
        return randomizeOrder;
    }

    public QuizQuestion randomizeOrder(Boolean randomizeOrder) {
        this.randomizeOrder = randomizeOrder;
        return this;
    }

    public void setRandomizeOrder(Boolean randomizeOrder) {
        this.randomizeOrder = randomizeOrder;
    }

    public Boolean isInvalid() {
        return invalid == null ? false : invalid;
    }

    public QuizQuestion invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public QuizQuestionStatistic getQuizQuestionStatistic() {
        return quizQuestionStatistic;
    }

    public QuizQuestion questionStatistic(QuizQuestionStatistic quizQuestionStatistic) {
        this.quizQuestionStatistic = quizQuestionStatistic;
        return this;
    }

    public void setQuizQuestionStatistic(QuizQuestionStatistic quizQuestionStatistic) {
        this.quizQuestionStatistic = quizQuestionStatistic;
    }

    public QuizExercise getExercise() {
        return exercise;
    }

    public QuizQuestion exercise(QuizExercise quizExercise) {
        this.exercise = quizExercise;
        return this;
    }

    public void setExercise(QuizExercise quizExercise) {
        this.exercise = quizExercise;
    }

    /**
     * Calculate the score for the given answer
     *
     * @param submittedAnswer The answer given for this question
     * @return the resulting score
     */
    public double scoreForAnswer(SubmittedAnswer submittedAnswer) {
        return ScoringStrategyFactory.makeScoringStrategy(this).calculateScore(this, submittedAnswer);
    }

    /**
     * Checks if the given answer is 100 % correct. This is independent of the scoring type
     *
     * @param submittedAnswer The answer given for this question
     * @return true, if the answer is 100% correct, false otherwise
     */
    public boolean isAnswerCorrect(SubmittedAnswer submittedAnswer) {
        return ScoringStrategyFactory.makeScoringStrategy(this).calculateScore(this, submittedAnswer) == getScore();
    }

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
        // check title
        return getTitle() != null && !getTitle().equals("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizQuestion quizQuestion = (QuizQuestion) o;
        if (quizQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizQuestion{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", text='" + getText() + "'" + ", hint='" + getHint() + "'" + ", explanation='"
                + getExplanation() + "'" + ", score='" + getScore() + "'" + ", scoringType='" + getScoringType() + "'" + ", randomizeOrder='" + isRandomizeOrder() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }

    /**
     * NOTE: do not use this in a transactional context and do not save the returned object to the database
     * This method is useful when we want to cut off attributes while sending entities to the client and we are only interested in the id of the object
     * We use polymorphism here, so subclasses should implement / override this method to create the correct object type
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
}
