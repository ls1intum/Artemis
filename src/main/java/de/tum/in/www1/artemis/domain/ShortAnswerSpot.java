package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A ShortAnswerSpot.
 */
@Entity
@Table(name = "short_answer_spot")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ShortAnswerSpot implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "spotNr")
    @JsonView(QuizView.Before.class)
    private Integer spotNr;

    @Column(name = "width")
    @JsonView(QuizView.Before.class)
    private Integer width;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    @ManyToOne
    @JsonIgnore
    private ShortAnswerQuestion question;

    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "spot")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ShortAnswerMapping> mappings = new HashSet<>();

    /**
     * tempID is needed to refer to spots that have not been persisted yet
     * in the correctMappings of a question (so user can create mappings in the UI before saving new spots)
     */
    @Transient
    // variable name must be different from Getter name,
    // so that Jackson ignores the @Transient annotation,
    // but Hibernate still respects it
    private Long tempIDTransient;

    public Long getTempID() {
        return tempIDTransient;
    }

    public void setTempID(Long tempID) {
        this.tempIDTransient = tempID;
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSpotNr() {
        return spotNr;
    }

    public ShortAnswerSpot spotNr(Integer spotNr) {
        this.spotNr = spotNr;
        return this;
    }

    public void setSpotNr(Integer spotNr) {
        this.spotNr = spotNr;
    }

    public Integer getWidth() {
        return width;
    }

    public ShortAnswerSpot width(Integer width) {
        this.width = width;
        return this;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Boolean isInvalid() {
        return invalid;
    }

    public ShortAnswerSpot invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public ShortAnswerSpot question(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
        return this;
    }

    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public ShortAnswerSpot mappings(Set<ShortAnswerMapping> mappings) {
        this.mappings = mappings;
        return this;
    }

    public ShortAnswerSpot addMappings(ShortAnswerMapping mapping) {
        this.mappings.add(mapping);
        mapping.setSpot(this);
        return this;
    }

    public ShortAnswerSpot removeMappings(ShortAnswerMapping mapping) {
        this.mappings.remove(mapping);
        mapping.setSpot(null);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShortAnswerSpot shortAnswerSpot = (ShortAnswerSpot) o;
        if (shortAnswerSpot.getTempID() != null && getTempID() != null && Objects.equals(getTempID(), shortAnswerSpot.getTempID())) {
            return true;
        }
        if (shortAnswerSpot.getId() == null || getId() == null) {
            return false;
        }

        return Objects.equals(getId(), shortAnswerSpot.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerSpot{" +
            "id=" + getId() +
            ", width=" + getWidth() +
            ", spotNr=" + getSpotNr() +
            ", invalid='" + isInvalid() + "'" +
            "}";
    }
}
