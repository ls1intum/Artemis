package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.enumeration.SpotType;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSpot.
 */
@Entity
@Table(name = "short_answer_spot")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpot extends TempIdObject {

    @Column(name = "spotNr")
    @JsonView(QuizView.Before.class)
    private Integer spotNr;

    @Column(name = "width")
    @JsonView(QuizView.Before.class)
    private Integer width;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    @JsonView(QuizView.Before.class)
    private SpotType type = SpotType.TEXT;

    @ManyToOne
    @JsonIgnore
    private ShortAnswerQuestion question;

    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "spot")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ShortAnswerMapping> mappings = new HashSet<>();

    public Integer getSpotNr() {
        return spotNr;
    }

    public ShortAnswerSpot spotNr(Integer spotNr) {
        this.spotNr = spotNr;
        return this;
    }

    public ShortAnswerSpot spotType(SpotType type) {
        this.type = type;
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
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public SpotType getType() {
        return type;
    }

    public void setType(SpotType type) {
        this.type = type;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
    }

    public Set<ShortAnswerMapping> getMappings() {
        return mappings;
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
    public String toString() {
        return "ShortAnswerSpot{" + "id=" + getId() + ", width=" + getWidth() + ", spotNr=" + getSpotNr() + ", invalid='" + isInvalid() + "', type='" + getType() + "'" + "}";
    }
}
