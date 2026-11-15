package de.tum.cit.aet.artemis.atlas.domain.practice;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

/**
 * A PracticeLog records the delivery of a single quiz question within a PracticeSession.
 * It stores whether the item was allocated adaptively or as non-adaptive control (50/50 split),
 * the student's submitted response, score, and the submission timestamp.
 */
@Entity
@Table(name = "practice_log")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PracticeLog extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_session_id", nullable = false)
    @Parent
    private PracticeSession practiceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_question_id", nullable = false)
    @Parent
    private QuizQuestion quizQuestion;

    @Column(name = "is_adaptive", nullable = false)
    private boolean isAdaptive;

    @Column(name = "score")
    private Double score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_answer")
    private String submittedAnswer;

    @Column(name = "answered_at")
    private ZonedDateTime answeredAt;

    public PracticeSession getPracticeSession() {
        return practiceSession;
    }

    public void setPracticeSession(PracticeSession practiceSession) {
        this.practiceSession = practiceSession;
    }

    public QuizQuestion getQuizQuestion() {
        return quizQuestion;
    }

    public void setQuizQuestion(QuizQuestion quizQuestion) {
        this.quizQuestion = quizQuestion;
    }

    public boolean isAdaptive() {
        return isAdaptive;
    }

    public void setAdaptive(boolean adaptive) {
        isAdaptive = adaptive;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public ZonedDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(ZonedDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    @Override
    public String toString() {
        return "PracticeLog{" + "id=" + getId() + ", practiceSession=" + (practiceSession != null ? practiceSession.getId() : null) + ", quizQuestion="
                + (quizQuestion != null ? quizQuestion.getId() : null) + ", isAdaptive=" + isAdaptive + ", score=" + score + ", answeredAt=" + answeredAt + '}';
    }
}
