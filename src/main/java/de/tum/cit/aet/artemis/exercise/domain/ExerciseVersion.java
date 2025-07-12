package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "exercise_version")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExerciseVersion extends AbstractAuditingEntity {

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    /**
     * This field contains a JSON representation of the fields in Exercise.
     * The content of this field is used to create a JSON representation of the Exercise object.
     * The content of this field is used to display the history of changes made to the exercise.
     * The content of this field is used to compare the current state of the exercise with the previous state.
     */
    @Column(name = "content", columnDefinition = "json")
    @Convert(converter = ExerciseVersionConverter.class)
    private ExerciseVersionContent content;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public ExerciseVersionContent getContent() {
        return content;
    }

    public void setContent(ExerciseVersionContent content) {
        this.content = content;
    }
}
