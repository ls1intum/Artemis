package de.tum.in.www1.artemis.domain.quiz;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A QuizBatch.
 */
@Entity
@Table(name = "quiz_batch")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizBatch extends DomainObject {

    @Nullable
    @Column(name = "start_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime startTime;

    @Nullable
    @Column(name = "password")
    private String password;

    @Column(name = "creator_id")
    @JsonIgnore
    private Long creator;

    @ManyToOne
    @JoinColumn(name = "quiz_id")
    @JsonIgnore
    private QuizExercise quizExercise;

    @Nullable
    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(@Nullable ZonedDateTime startTime) {
        this.startTime = startTime;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public void setCreator(Long creator) {
        this.creator = creator;
    }

    public Long getCreator() {
        return creator;
    }

    public QuizExercise getQuizExercise() {
        return quizExercise;
    }

    public void setQuizExercise(QuizExercise quizExercise) {
        this.quizExercise = quizExercise;
    }

    /**
     * Get the remaining time in seconds
     *
     * @return null, if the batch has not started, the remaining time in seconds otherwise
     */
    @JsonIgnore
    public Long getRemainingTime() {
        return isStarted()
                ? getQuizExercise() == null ? 0 : ChronoUnit.SECONDS.between(ZonedDateTime.now(), ChronoUnit.SECONDS.addTo(getStartTime(), getQuizExercise().getDuration()))
                : null;
    }

    /**
     * Check if the batch has started
     *
     * @return true if batch has started, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isStarted() {
        return getStartTime() != null && ZonedDateTime.now().isAfter(getStartTime());
    }

    /**
     * Check if submissions for this batch are allowed at the moment
     *
     * @return true if submissions are allowed, false otherwise
     */
    @JsonIgnore
    public Boolean isSubmissionAllowed() {
        return isStarted() && getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS > 0;
    }

    /**
     * Check if the batch has ended
     *
     * @return true if batch has ended, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isEnded() {
        return isStarted() && getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS <= 0;
    }

    @Override
    public String toString() {
        return "QuizBatch{" + "id=" + getId() + ", startTime=" + getStartTime() + "}";
    }
}
