package de.tum.cit.aet.artemis.programming.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class BuildTimeWindowTest {

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, ZoneOffset.UTC);

    private static final ZonedDateTime END = START.plusDays(7);

    @Test
    void isBuildAllowedAt_includesBothBounds() {
        var window = new BuildTimeWindow(START, END);

        assertThat(window.isBuildAllowedAt(START)).isTrue();
        assertThat(window.isBuildAllowedAt(END)).isTrue();
        assertThat(window.isBuildAllowedAt(START.plusDays(3))).isTrue();
        assertThat(window.isBuildAllowedAt(START.minusNanos(1))).isFalse();
        assertThat(window.isBuildAllowedAt(END.plusNanos(1))).isFalse();
    }

    @Test
    void isBuildAllowedAt_withoutAnEndOnlyTheStartRestricts() {
        var window = new BuildTimeWindow(START, null);

        assertThat(window.isBuildAllowedAt(START.minusSeconds(1))).isFalse();
        assertThat(window.isBuildAllowedAt(START)).isTrue();
        assertThat(window.isBuildAllowedAt(START.plusYears(10))).isTrue();
    }

    @Test
    void isBuildAllowedAt_withoutAStartOnlyTheEndRestricts() {
        var window = new BuildTimeWindow(null, END);

        assertThat(window.isBuildAllowedAt(END.minusYears(10))).isTrue();
        assertThat(window.isBuildAllowedAt(END)).isTrue();
        assertThat(window.isBuildAllowedAt(END.plusSeconds(1))).isFalse();
    }

    @Test
    void extendedByDueDateExtension_movesOnlyTheEndByTheExtension() {
        var window = new BuildTimeWindow(START, END);
        var dueDate = END.plusDays(1);

        var extended = window.extendedByDueDateExtension(dueDate, dueDate.plusHours(5));

        assertThat(extended).isEqualTo(new BuildTimeWindow(START, END.plusHours(5)));
        assertThat(extended.isBuildAllowedAt(END.plusHours(5))).isTrue();
        assertThat(extended.isBuildAllowedAt(END.plusHours(5).plusNanos(1))).isFalse();
    }

    @Test
    void extendedByDueDateExtension_keepsTheWindowWithoutAnExtension() {
        var window = new BuildTimeWindow(START, END);

        assertThat(window.extendedByDueDateExtension(END, null)).isSameAs(window);
        assertThat(window.extendedByDueDateExtension(null, END.plusDays(1))).isSameAs(window);
        assertThat(window.extendedByDueDateExtension(END, END)).isSameAs(window);
        // an individual due date before the due date of the exercise never shortens the window
        assertThat(window.extendedByDueDateExtension(END, END.minusDays(1))).isSameAs(window);
        assertThat(new BuildTimeWindow(START, null).extendedByDueDateExtension(END, END.plusDays(1))).isEqualTo(new BuildTimeWindow(START, null));
    }

    @Test
    void isWithin_checksBothBoundsAgainstBothDates() {
        var window = new BuildTimeWindow(START, END);

        assertThat(window.isWithin(START, END)).isTrue();
        assertThat(window.isWithin(START.minusDays(1), END.plusDays(1))).isTrue();
        assertThat(window.isWithin(START.plusNanos(1), END)).isFalse();
        assertThat(window.isWithin(START, END.minusNanos(1))).isFalse();
        assertThat(window.isWithin(null, null)).isTrue();
    }

    @Test
    void isWithin_ignoresMissingBoundsOfTheWindow() {
        assertThat(new BuildTimeWindow(START, null).isWithin(START.minusDays(1), END)).isTrue();
        assertThat(new BuildTimeWindow(START, null).isWithin(START.plusDays(1), END)).isFalse();
        assertThat(new BuildTimeWindow(null, END).isWithin(START, END.plusDays(1))).isTrue();
        assertThat(new BuildTimeWindow(null, END).isWithin(START, END.minusDays(1))).isFalse();
    }

    @Test
    void isBuildAllowedAt_comparesInstantsNotZones() {
        var window = new BuildTimeWindow(START, END);

        // 13:00 in Berlin (UTC+1 in winter) is 12:00 UTC, exactly the start
        assertThat(window.isBuildAllowedAt(ZonedDateTime.of(2026, 11, 2, 13, 0, 0, 0, ZoneId.of("Europe/Berlin")))).isTrue();
        assertThat(window.isBuildAllowedAt(ZonedDateTime.of(2026, 11, 2, 12, 59, 59, 0, ZoneId.of("Europe/Berlin")))).isFalse();
    }

    @Test
    void windowsOfTheSameInstantsAreEqualWhateverTheZone() {
        var utc = new BuildTimeWindow(START, END);
        var berlin = new BuildTimeWindow(START.withZoneSameInstant(ZoneId.of("Europe/Berlin")), END.withZoneSameInstant(ZoneId.of("Europe/Berlin")));

        assertThat(berlin).isEqualTo(utc);
        assertThat(berlin.start().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void normalized_turnsAWindowWithoutBoundsIntoNull() {
        assertThat(BuildTimeWindow.normalized(null)).isNull();
        assertThat(BuildTimeWindow.normalized(new BuildTimeWindow(null, null))).isNull();
        var window = new BuildTimeWindow(START, null);
        assertThat(BuildTimeWindow.normalized(window)).isSameAs(window);
    }
}
