package de.tum.cit.aet.artemis.quiz.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A ShortAnswerSpot.
 */
// No @Cache here on purpose: loaded via cascade during quiz submission merge. See #12574 / #12584.
@Entity
@Table(name = "short_answer_spot")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpot extends DomainObject implements QuizQuestionComponent<ShortAnswerQuestion> {

    @Column(name = "spotNr")
    private Integer spotNr;

    @Column(name = "width")
    private Integer width;

    @Column(name = "invalid")
    private Boolean invalid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private ShortAnswerQuestion question;

    // NOTE: without cascade and orphanRemoval, deletion of quizzes might not work properly, so we reference mappings here, even if we do not use them
    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "spot")
    @JsonIgnore
    private Set<ShortAnswerMapping> mappings = new HashSet<>();

    public Integer getSpotNr() {
        return spotNr;
    }

    public void setSpotNr(Integer spotNr) {
        this.spotNr = spotNr;
    }

    public ShortAnswerSpot spotNr(Integer spotNr) {
        this.spotNr = spotNr;
        return this;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public ShortAnswerSpot width(Integer width) {
        this.width = width;
        return this;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    @Override
    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
    }

    public void setMappings(Set<ShortAnswerMapping> shortAnswerMappings) {
        this.mappings = shortAnswerMappings;
    }

    @Override
    public String toString() {
        return "ShortAnswerSpot{" + "id=" + getId() + ", width=" + getWidth() + ", spotNr=" + getSpotNr() + ", invalid='" + isInvalid() + "'" + "}";
    }

}
