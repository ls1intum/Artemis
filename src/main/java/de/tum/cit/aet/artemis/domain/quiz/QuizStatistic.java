package de.tum.cit.aet.artemis.domain.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.DomainObject;

/**
 * A QuizStatistic.
 */
@Entity
@Table(name = "quiz_statistic")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizStatistic extends DomainObject {

    @Column(name = "participants_rated")
    private Integer participantsRated = 0;

    @Column(name = "participants_unrated")
    private Integer participantsUnrated = 0;

    public Integer getParticipantsRated() {
        return participantsRated;
    }

    public void setParticipantsRated(Integer participantsRated) {
        this.participantsRated = participantsRated;
    }

    public Integer getParticipantsUnrated() {
        return participantsUnrated;
    }

    public void setParticipantsUnrated(Integer participantsUnrated) {
        this.participantsUnrated = participantsUnrated;
    }

    @Override
    public String toString() {
        return "QuizStatistic{" + "participantsRated=" + participantsRated + ", participantsUnrated=" + participantsUnrated + '}';
    }
}
