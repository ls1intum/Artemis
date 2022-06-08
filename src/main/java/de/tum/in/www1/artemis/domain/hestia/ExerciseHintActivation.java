package de.tum.in.www1.artemis.domain.hestia;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "exercise_hint_activation")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseHintActivation extends DomainObject {

    @ManyToOne(optional = false)
    private User user;

    @ManyToOne(optional = false)
    private ExerciseHint exerciseHint;

    @Column(name = "activation_date", nullable = false)
    private ZonedDateTime activationDate;

    @Column(name = "rating")
    private Integer rating;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ExerciseHint getExerciseHint() {
        return exerciseHint;
    }

    public void setExerciseHint(ExerciseHint exerciseHint) {
        this.exerciseHint = exerciseHint;
    }

    public ZonedDateTime getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(ZonedDateTime activationDate) {
        this.activationDate = activationDate;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
