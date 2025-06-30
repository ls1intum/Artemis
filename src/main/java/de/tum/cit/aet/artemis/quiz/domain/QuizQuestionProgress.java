package de.tum.cit.aet.artemis.quiz.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import de.tum.cit.aet.artemis.core.domain.User;

@Entity
public class QuizQuestionProgress {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "quiz_question_id")
    private QuizQuestion quizQuestion;

    @Column(name = "progress_json", columnDefinition = "json")
    private String progressJson;

    @Column(name = "last_modified")
    private ZonedDateTime lastModified;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public QuizQuestion getQuizQuestion() {
        return quizQuestion;
    }

    public void setQuizQuestion(QuizQuestion quizQuestion) {
        this.quizQuestion = quizQuestion;
    }

    public String getProgressJson() {
        return progressJson;
    }

    public void setProgressJson(String progressJson) {
        this.progressJson = progressJson;
    }

    public ZonedDateTime getLastAnsweredAt() {
        return lastModified;
    }

    public void setLastAnsweredAt(ZonedDateTime lastAnsweredAt) {
        this.lastModified = lastAnsweredAt;
    }

}
