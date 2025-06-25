package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Date;

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

    @Column(name = "answered_correctly")
    private boolean answeredCorrectly;

    @Column(name = "times_answered")
    private Integer timesAnswered;

    @Column(name = "times_answered_correct")
    private Integer timesAnsweredCorrect;

    @Column(name = "times_answered_wrong")
    private Integer timesAnsweredWorng;

    @Column(name = "last_answered_at")
    private Date lastAnsweredAt;

    @Column(name = "last_answered_correct_at")
    private Date lastAnsweredCorrectAt;

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

    public boolean isAnsweredCorrectly() {
        return answeredCorrectly;
    }

    public void setAnsweredCorrectly(boolean answeredCorrectly) {
        this.answeredCorrectly = answeredCorrectly;
    }

    public Integer getTimesAnswered() {
        return timesAnswered;
    }

    public void setTimesAnswered(Integer timesAnswered) {
        this.timesAnswered = timesAnswered;
    }

    public Integer getTimesAnsweredCorrect() {
        return timesAnsweredCorrect;
    }

    public void setTimesAnsweredCorrect(Integer timesAnsweredCorrect) {
        this.timesAnsweredCorrect = timesAnsweredCorrect;
    }

    public Integer getTimesAnsweredWrong() {
        return timesAnsweredWorng;
    }

    public void setTimesAnsweredWrong(Integer timesAnsweredWrong) {
        this.timesAnsweredWorng = timesAnsweredWrong;
    }

    public Date getLastAnsweredAt() {
        return lastAnsweredAt;
    }

    public void setLastAnsweredAt(Date lastAnsweredAt) {
        this.lastAnsweredAt = lastAnsweredAt;
    }

    public Date getLastAnsweredCorrectAt() {
        return lastAnsweredCorrectAt;
    }

    public void setLastAnsweredCorrectAt(Date lastAnsweredCorrectAt) {
        this.lastAnsweredCorrectAt = lastAnsweredCorrectAt;
    }
}
