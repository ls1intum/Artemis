package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "json")
    private ExerciseVersionMetadata metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exercise_snapshot", columnDefinition = "json")
    private Exercise exerciseSnapshot;

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

    public ExerciseVersionMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ExerciseVersionMetadata metadata) {
        this.metadata = metadata;
    }

    public Exercise getExerciseSnapshot() {
        return exerciseSnapshot;
    }

    public void setExerciseSnapshot(Exercise exerciseSnapshot) {
        this.exerciseSnapshot = exerciseSnapshot;
    }
}
