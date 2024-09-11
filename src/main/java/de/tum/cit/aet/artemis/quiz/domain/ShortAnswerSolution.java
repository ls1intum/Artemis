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

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.TempIdObject;
import de.tum.cit.aet.artemis.quiz.config.QuizView;

/**
 * A ShortAnswerSolution.
 */
@Entity
@Table(name = "short_answer_solution")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSolution extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion> {

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private ShortAnswerQuestion question;

    // NOTE: without cascade and orphanRemoval, deletion of quizzes might not work properly, so we reference mappings here, even if we do not use them
    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "solution")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ShortAnswerMapping> mappings = new HashSet<>();

    public String getText() {
        return text;
    }

    public ShortAnswerSolution text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
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

    @Override
    public String toString() {
        return "ShortAnswerSolution{" + "id=" + getId() + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
