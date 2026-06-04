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
 * Every {@link de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent} for the
 * course appends to {@link #exerciseIds} and bumps {@link #lastEventTime}. The scheduler only
 * fires an orchestrator run once the debounce window has elapsed since the last event, so a burst
 * of edits collapses into a single run.
 * <p>
 * Records are serialisable because Hazelcast replicates entries across nodes and persists them in
 * the map's backup partitions; the accumulator must round-trip cleanly through Java serialization.
 * The id set is an unmodifiable snapshot; {@link #with} returns a fresh instance with merged ids.
 */
public record ContentChangeAccumulator(Set<Long> exerciseIds, Instant lastEventTime, int dailyRunCount, LocalDate dailyRunCountDate) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ContentChangeAccumulator {
        exerciseIds = exerciseIds == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(exerciseIds));
    }

    /** Empty initial state for a previously unseen course. */
    public static ContentChangeAccumulator empty(Instant now, LocalDate today) {
        return new ContentChangeAccumulator(Set.of(), now, 0, today);
    }

    /** Returns a fresh accumulator with the given exercise id merged into the buffered set. */
    public ContentChangeAccumulator with(long exerciseId, Instant now) {
        Set<Long> merged = new HashSet<>(exerciseIds);
        merged.add(exerciseId);
        return new ContentChangeAccumulator(merged, now, dailyRunCount, dailyRunCountDate);
    }

    /**
     * Clears the buffered id set after the scheduler claims it; the daily counter is bumped only
     * when {@code countAgainstCap} is {@code true} (i.e. the batch actually triggers an
     * orchestrator run, as opposed to a manual force-drain).
     */
    public ContentChangeAccumulator claim(LocalDate today, boolean countAgainstCap) {
        int baseCount = today.equals(dailyRunCountDate) ? dailyRunCount : 0;
        int newCount = countAgainstCap ? baseCount + 1 : baseCount;
        return new ContentChangeAccumulator(Set.of(), lastEventTime, newCount, today);
    }

    /** True when at least one exercise id is queued. */
    public boolean hasContent() {
        return !exerciseIds.isEmpty();
    }
}
