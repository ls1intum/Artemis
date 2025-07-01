package de.tum.cit.aet.artemis.quiz.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
public class QuizQuestionProgress extends DomainObject {

    @Column(name = "user_id")
    private long userId;

    @Column(name = "quiz_question_id")
    private long quizQuestionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress_json", columnDefinition = "json")
    private QuizQuestionProgressDataDAO progress;

    @Column(name = "last_modified")
    private ZonedDateTime lastModified;

    public long getUser() {
        return userId;
    }

    public void setUser(long userId) {
        this.userId = userId;
    }

    public long getQuizQuestion() {
        return quizQuestionId;
    }

    public void setQuizQuestion(long quizQuestionId) {
        this.quizQuestionId = quizQuestionId;
    }

    public QuizQuestionProgressDataDAO getProgressJson() {
        return progress;
    }

    public void setProgressJson(QuizQuestionProgressDataDAO progress) {
        this.progress = progress;
    }

    public ZonedDateTime getLastAnsweredAt() {
        return lastModified;
    }

    public void setLastAnsweredAt(ZonedDateTime lastAnsweredAt) {
        this.lastModified = lastAnsweredAt;
    }

}
