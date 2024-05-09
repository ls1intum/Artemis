package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

/**
 * A ShortAnswerSpot.
 */
public class ShortAnswerSpot implements QuizQuestionComponent<ShortAnswerQuestion>, Serializable {

    private Long id = 1L;

    private Integer spotNr;

    private Integer width;

    private Boolean invalid;

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
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public String toString() {
        return "ShortAnswerSpot{" + "id=" + getId() + ", width=" + getWidth() + ", spotNr=" + getSpotNr() + ", invalid='" + isInvalid() + "'" + "}";
    }

    @Override
    public void setQuestion(ShortAnswerQuestion quizQuestion) {

    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ShortAnswerSpot shortAnswerSpot = (ShortAnswerSpot) obj;
        if (shortAnswerSpot.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerSpot.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
