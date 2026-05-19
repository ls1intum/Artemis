package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-course debounce state kept in the Hazelcast {@code atlas-content-change-accumulator} map.
 * Every {@link de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent} or
 * lecture-unit notification for the course appends to the id sets and bumps
 * {@link #lastEventTime}. The scheduler only fires an orchestrator run once the debounce window
 * has elapsed since the last event, so a burst of edits collapses into a single run.
 * <p>
 * Records are serialisable because Hazelcast replicates entries across nodes and persists them in
 * the map's backup partitions; the accumulator must round-trip cleanly through Java serialization.
 * The id sets are unmodifiable snapshots; {@link #with} returns a fresh instance with merged ids.
 */
public record ContentChangeAccumulator(Set<Long> exerciseIds, Set<Long> lectureUnitIds, Instant lastEventTime, int dailyRunCount, LocalDate dailyRunCountDate)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ContentChangeAccumulator {
        exerciseIds = exerciseIds == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(exerciseIds));
        lectureUnitIds = lectureUnitIds == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(lectureUnitIds));
    }

    /** Empty initial state for a previously unseen course. */
    public static ContentChangeAccumulator empty(Instant now, LocalDate today) {
        return new ContentChangeAccumulator(Set.of(), Set.of(), now, 0, today);
    }

    /** Returns a fresh accumulator with the given id merged into the correct content set. */
    public ContentChangeAccumulator with(long contentId, boolean isLectureUnit, Instant now) {
        Set<Long> nextExercises = exerciseIds;
        Set<Long> nextLectureUnits = lectureUnitIds;
        if (isLectureUnit) {
            Set<Long> merged = new HashSet<>(lectureUnitIds);
            merged.add(contentId);
            nextLectureUnits = merged;
        }
        else {
            Set<Long> merged = new HashSet<>(exerciseIds);
            merged.add(contentId);
            nextExercises = merged;
        }
        return new ContentChangeAccumulator(nextExercises, nextLectureUnits, now, dailyRunCount, dailyRunCountDate);
    }

    /**
     * Clears the buffered id sets after the scheduler claims them, increments the daily counter,
     * and resets it when the day rolls over.
     */
    public ContentChangeAccumulator claim(LocalDate today) {
        int newCount = today.equals(dailyRunCountDate) ? dailyRunCount + 1 : 1;
        return new ContentChangeAccumulator(Set.of(), Set.of(), lastEventTime, newCount, today);
    }

    /** True when at least one exercise or lecture-unit id is queued. */
    public boolean hasContent() {
        return !exerciseIds.isEmpty() || !lectureUnitIds.isEmpty();
    }
}
