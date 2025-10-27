package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;

@Entity
@Table(name = "exercise_version")
public class ExerciseVersion extends AbstractAuditingEntity {

    // Direct ID access (for saving), not nullable because delete will cascade when an exercise is deleted
    @Column(name = "exercise_id", updatable = false, nullable = false)
    private Long exerciseId;

    // Direct ID access (for saving), not nullable because delete will cascade when an exercise is deleted
    @Column(name = "author_id", updatable = false, nullable = false)
    private Long authorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exercise_snapshot", columnDefinition = "json")
    private ExerciseSnapshotDTO exerciseSnapshotDTO;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public ExerciseSnapshotDTO getExerciseSnapshot() {
        return exerciseSnapshotDTO;
    }

    public void setExerciseSnapshot(ExerciseSnapshotDTO exerciseSnapshotDTO) {
        this.exerciseSnapshotDTO = exerciseSnapshotDTO;
    }

}
