package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisBuffer.PendingGroup;

class ErrorAnalysisBufferTest {

    private static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);

    private ErrorAnalysisBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new ErrorAnalysisBuffer(clock);
    }

    private static long hourOf(Instant instant) {
        return instant.toEpochMilli() / ErrorAnalysisBuffer.MILLIS_PER_HOUR;
    }

    @Test
    void groupsErrorsThatDifferOnlyInNumbersAndIdentifiers() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise 17");
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise 42");
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise   9");

        Map<String, PendingGroup> groups = buffer.snapshot();

        assertThat(groups).hasSize(1);
        PendingGroup group = groups.values().iterator().next();
        assertThat(group.pattern).isEqualTo("ExerciseService: Could not find exercise <n>");
        assertThat(group.perHour).containsExactly(Map.entry(hourOf(START), 3L));
    }

    @ParameterizedTest
    @CsvSource({ "Mail to barney.young@example.org failed, Mail to <email> failed", "Lost 6f1c1d9e-5a31-4c1b-9d2e-0a1b2c3d4e5f again, Lost <uuid> again",
            "Timeout after 30 seconds in 3 steps, Timeout after <n> seconds in <n> steps" })
    void replacesWhatTellsErrorsApartByPlaceholders(String message, String expectedPattern) {
        assertThat(ErrorAnalysisBuffer.toPattern(message)).isEqualTo(expectedPattern);
    }

    @Test
    void doesNotGroupErrorsOfDifferentLocationsOrSeverities() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "same message");
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "same message");
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.WARN, "same message");

        assertThat(buffer.snapshot()).hasSize(3);
    }

    @Test
    void doesNotCountAHint() {
        buffer.record(ErrorLocation.CLIENT, ErrorSeverity.INFO, "browser without passkeys");

        assertThat(buffer.snapshot()).isEmpty();
    }

    @Test
    void remembersWhenAnErrorHappenedFirstAndLastAndCountsPerHour() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        clock.advance(Duration.ofHours(5));
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        PendingGroup group = buffer.snapshot().values().iterator().next();

        assertThat(group.firstSeenMillis).isEqualTo(START.toEpochMilli());
        assertThat(group.lastSeenMillis).isEqualTo(START.plus(Duration.ofHours(5)).toEpochMilli());
        assertThat(group.perHour).containsExactly(Map.entry(hourOf(START), 1L), Map.entry(hourOf(START) + 5, 2L));
    }

    @Test
    void shortensALongMessage() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "x".repeat(5_000));

        assertThat(buffer.snapshot().values().iterator().next().pattern).hasSize(ErrorAnalysisBuffer.MAX_MESSAGE_LENGTH);
    }

    @Test
    void forgetsWhatIsOlderThanTheLongestTimeSpan() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        clock.advance(Duration.ofDays(31));
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        assertThat(buffer.snapshot().values().iterator().next().perHour).containsOnlyKeys(hourOf(START) + 31 * 24);
    }

    @Test
    void keepsAtMostTheAllowedNumberOfGroupsAndForgetsTheOneSeenLongestAgo() {
        for (int i = 0; i < ErrorAnalysisBuffer.MAX_GROUPS; i++) {
            buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "error kind " + (char) ('a' + i % 26) + (char) ('a' + i / 26 % 26) + (char) ('a' + i / 676));
            clock.advance(Duration.ofSeconds(1));
        }
        assertThat(buffer.snapshot()).hasSize(ErrorAnalysisBuffer.MAX_GROUPS);

        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "one more");

        Map<String, PendingGroup> groups = buffer.snapshot();
        assertThat(groups).hasSize(ErrorAnalysisBuffer.MAX_GROUPS);
        assertThat(groups.values()).extracting(group -> group.pattern).contains("one more");
        assertThat(groups.values()).extracting(group -> group.firstSeenMillis).doesNotContain(START.toEpochMilli());
    }

    @Test
    void drainTakesEverythingAndStartsAgain() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        Map<String, PendingGroup> drained = buffer.drain();

        assertThat(drained).hasSize(1);
        assertThat(buffer.snapshot()).isEmpty();
    }

    @Test
    void restoreAddsWhatWasNotWrittenToWhatWasCollectedMeanwhile() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        Map<String, PendingGroup> drained = buffer.drain();
        clock.advance(Duration.ofMinutes(1));
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        buffer.restore(drained);

        PendingGroup group = buffer.snapshot().values().iterator().next();
        assertThat(group.perHour).containsExactly(Map.entry(hourOf(START), 2L));
        assertThat(group.firstSeenMillis).isEqualTo(START.toEpochMilli());
        assertThat(group.lastSeenMillis).isEqualTo(START.plus(Duration.ofMinutes(1)).toEpochMilli());
    }

    @Test
    void aSnapshotIsACopy() {
        buffer.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        buffer.snapshot().values().iterator().next().perHour.clear();

        assertThat(buffer.snapshot().values().iterator().next().perHour).hasSize(1);
    }
}
