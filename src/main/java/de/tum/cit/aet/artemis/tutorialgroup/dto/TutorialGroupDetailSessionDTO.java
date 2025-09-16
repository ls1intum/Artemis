package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupDetailSessionData;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupDetailSessionDTO(@NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull String location, boolean isCancelled, boolean locationChanged,
        boolean timeChanged, boolean dateChanged, @Nullable Integer attendanceCount) {

    public static TutorialGroupDetailSessionDTO from(TutorialGroupDetailSessionData data, int scheduleDayOfWeek, LocalTime scheduleStart, LocalTime scheduleEnd,
            String scheduleLocation, ZoneId courseTimeZone) {
        boolean isCancelled = data.status() == TutorialGroupSessionStatus.CANCELLED;
        boolean sameLocation = isCancelled || data.location().equals(scheduleLocation);
        ZonedDateTime sessionStart = data.start().withZoneSameInstant(courseTimeZone);
        ZonedDateTime sessionEnd = data.end().withZoneSameInstant(courseTimeZone);
        boolean sameTime = isCancelled || sessionStart.toLocalTime().equals(scheduleStart) && sessionEnd.toLocalTime().equals(scheduleEnd);
        boolean sameDay = isCancelled || sessionStart.getDayOfWeek().getValue() == scheduleDayOfWeek;
        return new TutorialGroupDetailSessionDTO(data.start(), data.end(), data.location(), isCancelled, !sameLocation, !sameTime, !sameDay, data.attendanceCount());
    }
}
