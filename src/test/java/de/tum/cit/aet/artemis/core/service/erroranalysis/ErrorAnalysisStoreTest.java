package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;

class ErrorAnalysisStoreTest {

    private static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);

    private ErrorAnalysisStore store;

    @BeforeEach
    void setUp() {
        store = new ErrorAnalysisStore(clock);
    }

    private List<ErrorGroupDTO> query(ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity) {
        return store.query(span, location, minSeverity);
    }

    @Test
    void groupsErrorsThatDifferOnlyInNumbersAndIdentifiers() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise 17");
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise 42");
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise 42");
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "ExerciseService: Could not find exercise   9");

        List<ErrorGroupDTO> groups = query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR);

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.message()).isEqualTo("ExerciseService: Could not find exercise <n>");
            assertThat(group.count()).isEqualTo(4);
        });
    }

    @ParameterizedTest
    @CsvSource({ "Mail to barney.young@example.org failed, Mail to <email> failed", "Lost 6f1c1d9e-5a31-4c1b-9d2e-0a1b2c3d4e5f again, Lost <uuid> again",
            "Timeout after 30 seconds in 3 steps, Timeout after <n> seconds in <n> steps" })
    void replacesWhatTellsErrorsApartByPlaceholders(String message, String expectedPattern) {
        store.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, message);

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).singleElement().extracting(ErrorGroupDTO::message).isEqualTo(expectedPattern);
    }

    @Test
    void doesNotGroupErrorsOfDifferentLocationsOrSeverities() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "same message");
        store.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "same message");
        store.record(ErrorLocation.CLIENT, ErrorSeverity.WARN, "same message");

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.WARN)).hasSize(3);
    }

    @Test
    void doesNotCountAHint() {
        store.record(ErrorLocation.CLIENT, ErrorSeverity.INFO, "browser without passkeys");

        assertThat(query(ErrorAnalysisSpan.MONTH, null, ErrorSeverity.INFO)).isEmpty();
    }

    @Test
    void remembersWhenAnErrorHappenedFirstAndLast() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        clock.advance(Duration.ofHours(5));
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).singleElement().satisfies(group -> {
            assertThat(group.firstSeen()).isEqualTo(START);
            assertThat(group.lastSeen()).isEqualTo(START.plus(Duration.ofHours(5)));
            assertThat(group.count()).isEqualTo(2);
        });
    }

    @Test
    void countsOnlyTheErrorsOfTheSelectedTimeSpan() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "old and new");
        clock.advance(Duration.ofDays(3));
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "old and new");
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "only recent");
        clock.advance(Duration.ofHours(2));

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message, ErrorGroupDTO::count)
                .containsExactlyInAnyOrder(tuple("old and new", 1L), tuple("only recent", 1L));
        assertThat(query(ErrorAnalysisSpan.WEEK, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message, ErrorGroupDTO::count)
                .containsExactlyInAnyOrder(tuple("old and new", 2L), tuple("only recent", 1L));
    }

    @Test
    void keepsTheFirstSeenOfAGroupThatIsOlderThanTheTimeSpan() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        clock.advance(Duration.ofDays(3));
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).singleElement().satisfies(group -> {
            assertThat(group.firstSeen()).isEqualTo(START);
            assertThat(group.count()).isEqualTo(1);
        });
    }

    @Test
    void leavesOutAGroupThatDidNotHappenInTheTimeSpan() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "long ago");
        clock.advance(Duration.ofDays(10));

        assertThat(query(ErrorAnalysisSpan.WEEK, null, ErrorSeverity.ERROR)).isEmpty();
        assertThat(query(ErrorAnalysisSpan.MONTH, null, ErrorSeverity.ERROR)).hasSize(1);
    }

    @Test
    void forgetsWhatIsOlderThanTheLongestTimeSpan() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");
        clock.advance(Duration.ofDays(31));
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "boom");

        assertThat(query(ErrorAnalysisSpan.MONTH, null, ErrorSeverity.ERROR)).singleElement().extracting(ErrorGroupDTO::count).isEqualTo(1L);
    }

    @Test
    void filtersByLocation() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "server error");
        store.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "client error");

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("server error", "client error");
        assertThat(query(ErrorAnalysisSpan.DAY, ErrorLocation.SERVER, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message).containsExactly("server error");
        assertThat(query(ErrorAnalysisSpan.DAY, ErrorLocation.CLIENT, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message).containsExactly("client error");
    }

    @Test
    void filtersBySeverityAsAMinimum() {
        store.record(ErrorLocation.CLIENT, ErrorSeverity.WARN, "a warning");
        store.record(ErrorLocation.CLIENT, ErrorSeverity.ERROR, "an error");
        store.record(ErrorLocation.CLIENT, ErrorSeverity.CRITICAL, "a critical error");

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.WARN)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("a warning", "an error",
                "a critical error");
        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message).containsExactlyInAnyOrder("an error", "a critical error");
        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.CRITICAL)).extracting(ErrorGroupDTO::message).containsExactly("a critical error");
    }

    @Test
    void listsTheMostFrequentGroupFirstAndTheMostRecentOfEqualOnes() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "rare and old");
        clock.advance(Duration.ofMinutes(1));
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "rare and new");
        for (int i = 0; i < 3; i++) {
            store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "frequent");
        }

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).extracting(ErrorGroupDTO::message).containsExactly("frequent", "rare and new", "rare and old");
    }

    @Test
    void shortensALongMessage() {
        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "x".repeat(5_000));

        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).singleElement().extracting(group -> group.message().length())
                .isEqualTo(ErrorAnalysisStore.MAX_MESSAGE_LENGTH);
    }

    @Test
    void keepsAtMostTheAllowedNumberOfGroupsAndForgetsTheOneSeenLongestAgo() {
        for (int i = 0; i < ErrorAnalysisStore.MAX_GROUPS; i++) {
            store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR,
                    "error kind " + "x".repeat(i % 7 + 1) + (char) ('a' + i % 26) + (char) ('a' + i / 26 % 26) + (char) ('a' + i / 676));
            clock.advance(Duration.ofSeconds(1));
        }
        assertThat(query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR)).hasSize(ErrorAnalysisStore.MAX_GROUPS);

        store.record(ErrorLocation.SERVER, ErrorSeverity.ERROR, "one more");

        List<ErrorGroupDTO> groups = query(ErrorAnalysisSpan.DAY, null, ErrorSeverity.ERROR);
        assertThat(groups).hasSize(ErrorAnalysisStore.MAX_GROUPS);
        assertThat(groups).extracting(ErrorGroupDTO::message).contains("one more");
        assertThat(groups).extracting(ErrorGroupDTO::firstSeen).doesNotContain(START);
    }

    /** A clock that only moves when the test says so. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
