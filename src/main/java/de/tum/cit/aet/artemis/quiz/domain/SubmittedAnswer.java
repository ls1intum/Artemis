package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.quiz.config.QuizView;

/**
 * A SubmittedAnswer.
 */
@Entity
@Table(name = "submitted_answer")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

// add JsonTypeInfo and JsonSubTypes annotation to help Jackson decide which class the JSON should be deserialized to
// depending on the value of the "type" property.
// Note: The "type" property has to be added on the front-end when making a request that includes a SubmittedAnswer Object
// However, the "type" property will be automatically added by Jackson when an object is serialized
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswer.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropSubmittedAnswer.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswer.class, name = "short-answer")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class SubmittedAnswer extends DomainObject {

    @Column(name = "score_in_points")
    @JsonView(QuizView.After.class)
    private Double scoreInPoints;

    @ManyToOne
    @JsonIgnoreProperties({ "questionStatistic", "exercise" })
    @JsonView(QuizView.Before.class)
    private QuizQuestion quizQuestion;

    @ManyToOne
    @JsonIgnore
    private AbstractQuizSubmission submission;

    public Double getScoreInPoints() {
        return scoreInPoints;
    }

    public void setScoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
    }

    public QuizQuestion getQuizQuestion() {
        return quizQuestion;
    }

    public void setQuizQuestion(QuizQuestion quizQuestion) {
        this.quizQuestion = quizQuestion;
    }

    public AbstractQuizSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(AbstractQuizSubmission quizSubmission) {
        this.submission = quizSubmission;
    }

    /**
     * Filter out information about correct answers.
     * Calls {@link QuizQuestion#filterForStudentsDuringQuiz()} which removes all relevant fields.
     * Dynamic binding will call the right overridden method for different question types.
     */
    public void filterOutCorrectAnswers() {
        QuizQuestion question = this.getQuizQuestion();
        if (question != null) {
            question.filterForStudentsDuringQuiz();
        }
        this.setScoreInPoints(null);
    }

    /**
     * Delete all references to quizQuestion and quizQuestion-elements if the quiz was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    public abstract void checkAndDeleteReferences(QuizExercise quizExercise);
}
