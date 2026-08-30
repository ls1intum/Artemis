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
 * Every {@link de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent} appends to
 * {@link #exerciseIds} and every {@link de.tum.cit.aet.artemis.lecture.domain.event.LectureUnitContentChangedEvent}
 * appends to {@link #lectureUnitIds}; both bump {@link #lastEventTime}. The scheduler only fires an
 * orchestrator run once the debounce window has elapsed since the last event, so a burst of edits
 * across exercises and lecture units collapses into a single run.
 * <p>
 * Records are serialisable because Hazelcast replicates entries across nodes and persists them in
 * the map's backup partitions; the accumulator must round-trip cleanly through Java serialization.
 * The id sets are unmodifiable snapshots; {@link #with} / {@link #withLectureUnit} return fresh
 * instances with merged ids. The compact constructor null-guards {@link #lectureUnitIds} so an entry
 * serialized by an older node (before the field existed) deserializes to an empty set instead of
 * {@code null} — a rolling-upgrade safety net; {@code serialVersionUID} stays {@code 1L} so the two
 * shapes remain wire-compatible.
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

    /** Returns a fresh accumulator with the given exercise id merged into the buffered exercise set. */
    public ContentChangeAccumulator with(long exerciseId, Instant now) {
        Set<Long> merged = new HashSet<>(exerciseIds);
        merged.add(exerciseId);
        return new ContentChangeAccumulator(merged, lectureUnitIds, now, dailyRunCount, dailyRunCountDate);
    }

    /** Returns a fresh accumulator with the given lecture-unit id merged into the buffered lecture-unit set. */
    public ContentChangeAccumulator withLectureUnit(long lectureUnitId, Instant now) {
        Set<Long> merged = new HashSet<>(lectureUnitIds);
        merged.add(lectureUnitId);
        return new ContentChangeAccumulator(exerciseIds, merged, now, dailyRunCount, dailyRunCountDate);
    }

    /**
     * Clears both buffered id sets after the scheduler claims them; the daily counter is bumped only
     * when {@code countAgainstCap} is {@code true} (i.e. the batch actually triggers an
     * orchestrator run, as opposed to a manual force-drain).
     */
    public ContentChangeAccumulator claim(LocalDate today, boolean countAgainstCap) {
        int baseCount = today.equals(dailyRunCountDate) ? dailyRunCount : 0;
        int newCount = countAgainstCap ? baseCount + 1 : baseCount;
        return new ContentChangeAccumulator(Set.of(), Set.of(), lastEventTime, newCount, today);
    }

    /**
     * Releases one daily-run reservation taken by {@link #claim}. Used when a claimed batch could
     * not actually run because a concurrent orchestration held the course lock and is being
     * requeued — without this the optimistic reservation would permanently burn quota on every
     * retry tick. Floors at zero and resets the counter when the stored date is no longer today.
     */
    public ContentChangeAccumulator refundDailyRun(LocalDate today) {
        int baseCount = today.equals(dailyRunCountDate) ? dailyRunCount : 0;
        return new ContentChangeAccumulator(exerciseIds, lectureUnitIds, lastEventTime, Math.max(0, baseCount - 1), today);
    }

    /** True when at least one exercise id or lecture-unit id is queued. */
    public boolean hasContent() {
        return !exerciseIds.isEmpty() || !lectureUnitIds.isEmpty();
    }
}
