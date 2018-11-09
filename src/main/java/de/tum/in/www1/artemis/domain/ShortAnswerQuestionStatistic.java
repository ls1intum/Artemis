package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A ShortAnswerQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value="SA")
@JsonTypeName("short-answer")
public class ShortAnswerQuestionStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true, mappedBy = "shortAnswerQuestionStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ShortAnswerSpotCounter> shortAnswerSpotCounters = new HashSet<>();
    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public Set<ShortAnswerSpotCounter> getShortAnswerSpotCounters() {
        return shortAnswerSpotCounters;
    }

    public ShortAnswerQuestionStatistic shortAnswerSpotCounters(Set<ShortAnswerSpotCounter> shortAnswerSpotCounters) {
        this.shortAnswerSpotCounters = shortAnswerSpotCounters;
        return this;
    }

    public ShortAnswerQuestionStatistic addShortAnswerSpotCounters(ShortAnswerSpotCounter shortAnswerSpotCounter) {
        this.shortAnswerSpotCounters.add(shortAnswerSpotCounter);
        shortAnswerSpotCounter.setShortAnswerQuestionStatistic(this);
        return this;
    }

    public ShortAnswerQuestionStatistic removeShortAnswerSpotCounters(ShortAnswerSpotCounter shortAnswerSpotCounter) {
        this.shortAnswerSpotCounters.remove(shortAnswerSpotCounter);
        shortAnswerSpotCounter.setShortAnswerQuestionStatistic(null);
        return this;
    }

    public void setShortAnswerSpotCounters(Set<ShortAnswerSpotCounter> shortAnswerSpotCounters) {
        this.shortAnswerSpotCounters = shortAnswerSpotCounters;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShortAnswerQuestionStatistic shortAnswerQuestionStatistic = (ShortAnswerQuestionStatistic) o;
        if (shortAnswerQuestionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerQuestionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerQuestionStatistic{" +
            "id=" + getId() +
            "}";
    }

    @Override
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated) {
        //TODO Francisco implement
    }

    @Override
    public void removeOldResult(SubmittedAnswer submittedAnswer, boolean rated) {
        //TODO Francisco implement
    }

    @Override
    public void resetStatistic() {
        //TODO Francisco implement
    }
}
