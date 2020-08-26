package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A QuizStatisticCounter.
 */
@Entity
@Table(name = "quiz_statistic_counter")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "SC")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizStatisticCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rated_counter")
    private Integer ratedCounter = 0;

    @Column(name = "un_rated_counter")
    private Integer unRatedCounter = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizStatisticCounter quizStatisticCounter = (QuizStatisticCounter) o;
        if (quizStatisticCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizStatisticCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
