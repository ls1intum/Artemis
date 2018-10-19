package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.enumeration.ScoringType;

/**
 * A Question.
 */
@Entity
@Table(name = "question")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "text")
    private String text;

    @Column(name = "hint")
    private String hint;

    @Column(name = "explanation")
    private String explanation;

    @Column(name = "score")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_type")
    private ScoringType scoringType;

    @Column(name = "randomize_order")
    private Boolean randomizeOrder;

    @Column(name = "invalid")
    private Boolean invalid;

    @OneToOne    @JoinColumn(unique = true)
    private QuestionStatistic questionStatistic;

    @ManyToOne
    @JsonIgnoreProperties("questions")
    private QuizExercise exercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public Question title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public Question text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHint() {
        return hint;
    }

    public Question hint(String hint) {
        this.hint = hint;
        return this;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public Question explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getScore() {
        return score;
    }

    public Question score(Integer score) {
        this.score = score;
        return this;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public ScoringType getScoringType() {
        return scoringType;
    }

    public Question scoringType(ScoringType scoringType) {
        this.scoringType = scoringType;
        return this;
    }

    public void setScoringType(ScoringType scoringType) {
        this.scoringType = scoringType;
    }

    public Boolean isRandomizeOrder() {
        return randomizeOrder;
    }

    public Question randomizeOrder(Boolean randomizeOrder) {
        this.randomizeOrder = randomizeOrder;
        return this;
    }

    public void setRandomizeOrder(Boolean randomizeOrder) {
        this.randomizeOrder = randomizeOrder;
    }

    public Boolean isInvalid() {
        return invalid;
    }

    public Question invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public QuestionStatistic getQuestionStatistic() {
        return questionStatistic;
    }

    public Question questionStatistic(QuestionStatistic questionStatistic) {
        this.questionStatistic = questionStatistic;
        return this;
    }

    public void setQuestionStatistic(QuestionStatistic questionStatistic) {
        this.questionStatistic = questionStatistic;
    }

    public QuizExercise getExercise() {
        return exercise;
    }

    public Question exercise(QuizExercise quizExercise) {
        this.exercise = quizExercise;
        return this;
    }

    public void setExercise(QuizExercise quizExercise) {
        this.exercise = quizExercise;
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
        Question question = (Question) o;
        if (question.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), question.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Question{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", text='" + getText() + "'" +
            ", hint='" + getHint() + "'" +
            ", explanation='" + getExplanation() + "'" +
            ", score=" + getScore() +
            ", scoringType='" + getScoringType() + "'" +
            ", randomizeOrder='" + isRandomizeOrder() + "'" +
            ", invalid='" + isInvalid() + "'" +
            "}";
    }
}
