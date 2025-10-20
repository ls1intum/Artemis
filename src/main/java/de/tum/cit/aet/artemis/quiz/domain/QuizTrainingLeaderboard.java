package de.tum.cit.aet.artemis.quiz.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizTrainingLeaderboard extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private Course course;

    // The league the student is in (1-5)
    @Column(nullable = false, name = "league")
    @Min(1)
    @Max(5)
    private int league;

    // The leaderboard score of the student
    @Column(nullable = false, name = "score")
    @PositiveOrZero
    private int score;

    // The number of questions the student answered correctly in total
    @Column(nullable = false, name = "answered_correctly")
    private int answeredCorrectly;

    // The number of questions the student answered wrong in total
    @Column(nullable = false, name = "answered_wrong")
    private int answeredWrong;

    // The due date for the next quiz training session
    @Column(nullable = false, name = "due_date")
    private ZonedDateTime dueDate;

    // The current streak the student is on (number of training sessions done at the exact due date)
    @Column(nullable = false, name = "streak")
    private int streak;

    // Flag indicating whether the user wants to be shown in the leaderboard or not
    @Column(nullable = false, name = "show_in_leaderboard")
    private boolean showInLeaderboard;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public int getLeague() {
        return league;
    }

    public void setLeague(int league) {
        this.league = league;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getAnsweredCorrectly() {
        return answeredCorrectly;
    }

    public void setAnsweredCorrectly(int answeredCorrectly) {
        this.answeredCorrectly = answeredCorrectly;
    }

    public int getAnsweredWrong() {
        return answeredWrong;
    }

    public void setAnsweredWrong(int answeredWrong) {
        this.answeredWrong = answeredWrong;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public boolean isShowInLeaderboard() {
        return showInLeaderboard;
    }

    public void setShowInLeaderboard(boolean showInLeaderboard) {
        this.showInLeaderboard = showInLeaderboard;
    }
}
