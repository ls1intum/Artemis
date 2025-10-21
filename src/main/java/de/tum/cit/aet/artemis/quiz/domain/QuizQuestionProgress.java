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

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "quiz_question_id")
    private long quizQuestionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress_json", columnDefinition = "json")
    private QuizQuestionProgressData progress;

    @Column(name = "last_modified")
    private ZonedDateTime lastModified;

    @Column(name = "dueDate")
    private ZonedDateTime dueDate;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public long getQuizQuestionId() {
        return quizQuestionId;
    }

    public void setQuizQuestionId(long quizQuestionId) {
        this.quizQuestionId = quizQuestionId;
    }

    public QuizQuestionProgressData getProgressJson() {
        return progress;
    }

    public void setProgressJson(QuizQuestionProgressData progress) {
        this.progress = progress;
    }

    public ZonedDateTime getLastAnsweredAt() {
        return lastModified;
    }

    public void setLastAnsweredAt(ZonedDateTime lastAnsweredAt) {
        this.lastModified = lastAnsweredAt;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }
}
