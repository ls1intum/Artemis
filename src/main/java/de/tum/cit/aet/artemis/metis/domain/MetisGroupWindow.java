package de.tum.cit.aet.artemis.metis.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * A time window for one tutorial group in which its students may take the Metis comprehension check of one exercise,
 * overriding the default window of the exercise's {@link MetisExerciseConfig}. Groups that meet on different days can
 * this way be given different slots without changing the exercise-wide setting.
 *
 * <p>
 * One row per (exercise, tutorial group) pair, which the schema keeps unique. Both keys are parents: the window has no
 * meaning without its configuration or without its group and cascades away with either.
 */
@Entity
@Table(name = "metis_group_window")
public class MetisGroupWindow extends DomainObject {

    /** The exercise configuration the window overrides. A parent: the row goes with the configuration. */
    @Column(name = "exercise_id", nullable = false)
    @Parent
    private long exerciseId;

    /** The tutorial group the window applies to. A parent: the row goes with the group. */
    @Column(name = "tutorial_group_id", nullable = false)
    @Parent
    private long tutorialGroupId;

    /** Start of the window for this group. */
    @Column(name = "window_start", nullable = false)
    private ZonedDateTime windowStart;

    /** End of the window for this group. */
    @Column(name = "window_end", nullable = false)
    private ZonedDateTime windowEnd;

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public long getTutorialGroupId() {
        return tutorialGroupId;
    }

    public void setTutorialGroupId(long tutorialGroupId) {
        this.tutorialGroupId = tutorialGroupId;
    }

    public ZonedDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(ZonedDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public ZonedDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(ZonedDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }
}
