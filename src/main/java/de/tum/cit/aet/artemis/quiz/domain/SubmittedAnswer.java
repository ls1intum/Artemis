package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.ConcreteProxy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A SubmittedAnswer.
 */
@Entity
@Table(name = "submitted_answer")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@ConcreteProxy

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
    private Double scoreInPoints;

    @ManyToOne
    @JsonIgnoreProperties({ "questionStatistic", "exercise" })
    @JoinColumn(nullable = false)
    private QuizQuestion quizQuestion;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(nullable = false)
    private QuizSubmission submission;

    // The student's submitted selection, stored as JSON instead of separate relational child tables/join tables (see SubmittedAnswerSelection). All three submitted-answer types
    // (drag-and-drop, multiple-choice, short-answer) use it.
    // @JsonIgnore because this is an internal storage representation: subclasses expose the selection through their existing getters (e.g. getMappings()),
    // preserving the REST/websocket wire format.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selection")
    @JsonIgnore
    private SubmittedAnswerSelection selection;

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

    public QuizSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(QuizSubmission quizSubmission) {
        this.submission = quizSubmission;
    }

    protected SubmittedAnswerSelection getSelection() {
        return selection;
    }

    protected void setSelection(SubmittedAnswerSelection selection) {
        this.selection = selection;
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
