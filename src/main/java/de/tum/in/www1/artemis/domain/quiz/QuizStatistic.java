package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * A QuizStatistic.
 */
@Entity
@Table(name = "quiz_statistic")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "S")
@DiscriminatorOptions(force = true)
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
}
