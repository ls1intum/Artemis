package de.tum.cit.aet.artemis.tutorialgroup.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TutorialGroupScheduleComparisonTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("recurrenceChanges")
    void recurrenceChangesMustRegenerateSessionsEvenWhenTheLocationAlsoChanges(String field, Consumer<TutorialGroupSchedule> change) {
        var original = schedule();
        var changed = schedule();
        change.accept(changed);

        assertThat(original.sameSchedule(changed)).as(field).isFalse();
        assertThat(changed.sameSchedule(original)).as(field).isFalse();
        assertThat(original.onlyLocationChanged(changed)).isFalse();
        changed.setLocation("New room");
        assertThat(original.onlyLocationChanged(changed)).isFalse();
        assertThat(original.getLocation()).isEqualTo("Room 1");
    }

    private static Stream<Arguments> recurrenceChanges() {
        return Stream.of(change("weekday", s -> s.setDayOfWeek(2)), change("start time", s -> s.setStartTime("11:00")), change("end time", s -> s.setEndTime("13:00")),
                change("frequency", s -> s.setRepetitionFrequency(2)), change("valid from", s -> s.setValidFromInclusive("2026-05-01")),
                change("valid to", s -> s.setValidToInclusive("2026-08-01")), change("missing weekday", s -> s.setDayOfWeek(null)),
                change("missing start", s -> s.setStartTime(null)), change("missing end", s -> s.setEndTime(null)),
                change("missing frequency", s -> s.setRepetitionFrequency(null)), change("missing start date", s -> s.setValidFromInclusive(null)),
                change("missing end date", s -> s.setValidToInclusive(null)));
    }

    private static Arguments change(String field, Consumer<TutorialGroupSchedule> change) {
        return Arguments.of(field, change);
    }

    @Test
    void identifiesLocationOnlyUpdatesAndIgnoresPersistenceIdentityAndGeneratedSessions() {
        var original = schedule();
        var same = schedule();
        original.setId(1L);
        same.setId(2L);
        assertThat(original.sameSchedule(same)).isTrue();
        assertThat(original.onlyLocationChanged(same)).isFalse();
        same.setLocation(null);
        assertThat(original.sameSchedule(same)).isFalse();
        assertThat(original.onlyLocationChanged(same)).isTrue();
        assertThat(same.onlyLocationChanged(original)).isTrue();
        same.setLocation("Room 2");
        assertThat(original.onlyLocationChanged(same)).isTrue();
        assertThat(original.getLocation()).isEqualTo("Room 1");
    }

    private static TutorialGroupSchedule schedule() {
        var schedule = new TutorialGroupSchedule();
        schedule.setDayOfWeek(1);
        schedule.setStartTime("10:00");
        schedule.setEndTime("12:00");
        schedule.setRepetitionFrequency(1);
        schedule.setValidFromInclusive("2026-04-01");
        schedule.setValidToInclusive("2026-07-31");
        schedule.setLocation("Room 1");
        return schedule;
    }
}
