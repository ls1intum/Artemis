package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import org.hibernate.annotations.ConcreteProxy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A QuizStatisticCounter.
 */
// No @Cache here on purpose: incremented on every quiz evaluation. See #12574 / #12584.
@Entity
@Table(name = "quiz_statistic_counter")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "SC")
@ConcreteProxy
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizStatisticCounter extends DomainObject {

    @Column(name = "rated_counter")
    private Integer ratedCounter = 0;

    @Column(name = "un_rated_counter")
    private Integer unRatedCounter = 0;

    public Integer getRatedCounter() {
        return ratedCounter;
    }

    public void setRatedCounter(Integer ratedCounter) {
        this.ratedCounter = ratedCounter;
    }

    public Integer getUnRatedCounter() {
        return unRatedCounter;
    }

    public void setUnRatedCounter(Integer unRatedCounter) {
        this.unRatedCounter = unRatedCounter;
    }
}
