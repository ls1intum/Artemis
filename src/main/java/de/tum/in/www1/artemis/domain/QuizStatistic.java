package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A QuizStatistic.
 */
@Entity
@Table(name = "quiz_statistic")
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name="discriminator",
    discriminatorType=DiscriminatorType.STRING
)
@DiscriminatorValue(value="S")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class QuizStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participants_rated")
    private Integer participantsRated = 0;

    @Column(name = "participants_unrated")
    private Integer participantsUnrated = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getParticipantsRated() {
        return participantsRated;
    }

    public QuizStatistic participantsRated(Integer participantsRated) {
        this.participantsRated = participantsRated;
        return this;
    }

    public void setParticipantsRated(Integer participantsRated) {
        this.participantsRated = participantsRated;
    }

    public Integer getParticipantsUnrated() {
        return participantsUnrated;
    }

    public QuizStatistic participantsUnrated(Integer participantsUnrated) {
        this.participantsUnrated = participantsUnrated;
        return this;
    }

    public void setParticipantsUnrated(Integer participantsUnrated) {
        this.participantsUnrated = participantsUnrated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizStatistic quizStatistic = (QuizStatistic) o;
        if (quizStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizStatistic{" +
            "id=" + getId() +
            ", participantsRated='" + getParticipantsRated() + "'" +
            ", participantsUnrated='" + getParticipantsUnrated() + "'" +
            "}";
    }
}
