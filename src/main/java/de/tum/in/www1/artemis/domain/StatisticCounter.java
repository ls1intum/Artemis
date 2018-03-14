package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A StatisticCounter.
 */
@Entity
@Table(name = "statistic_counter")
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name="discriminator",
    discriminatorType=DiscriminatorType.STRING
)
@DiscriminatorValue(value="SC")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public abstract class StatisticCounter implements Serializable {

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

    public StatisticCounter ratedCounter(Integer ratedCounter) {
        this.ratedCounter = ratedCounter;
        return this;
    }

    public void setRatedCounter(Integer ratedCounter) {
        this.ratedCounter = ratedCounter;
    }

    public Integer getUnRatedCounter() {
        return unRatedCounter;
    }

    public StatisticCounter unRatedCounter(Integer unRatedCounter) {
        this.unRatedCounter = unRatedCounter;
        return this;
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
        StatisticCounter statisticCounter = (StatisticCounter) o;
        if (statisticCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), statisticCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "StatisticCounter{" +
            "id=" + getId() +
            ", ratedCounter='" + getRatedCounter() + "'" +
            ", unRatedCounter='" + getUnRatedCounter() + "'" +
            "}";
    }
}
