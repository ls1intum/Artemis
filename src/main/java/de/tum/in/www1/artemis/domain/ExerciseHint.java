package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A ExerciseHint.
 */
@Entity
@Table(name = "exercise_hint")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExerciseHint extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @ManyToOne
    @JsonIgnoreProperties("exerciseHints")
    private Exercise exercise;

    public String getTitle() {
        return title;
    }

    public ExerciseHint title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public ExerciseHint content(String content) {
        this.content = content;
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ExerciseHint exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "ExerciseHint{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", content='" + getContent() + "'" + "}";
    }
}
