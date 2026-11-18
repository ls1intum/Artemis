package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A ShortAnswerSpot of a {@link ShortAnswerQuestion}.
 * <p>
 * Formerly a JPA entity backed by the {@code short_answer_spot} table; it is now a plain POJO stored inside the question's {@code content} JSON column (see
 * {@link ShortAnswerQuestionContent}). It no longer holds a back-reference to the question or to its mappings. Mirrors {@link DropLocation}.
 * <p>
 * It still extends {@link DomainObject} to reuse the {@code id} field and its id-based {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this class
 * is no longer an {@code @Entity}. The {@code id} is question-scoped (unique within the owning question, not globally).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpot extends DomainObject {

    private Integer spotNr;

    private Integer width;

    private Boolean invalid;

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

    @Override
    public String toString() {
        return "ShortAnswerSpot{" + "id=" + getId() + ", width=" + getWidth() + ", spotNr=" + getSpotNr() + ", invalid='" + isInvalid() + "'" + "}";
    }

}
