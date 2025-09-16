package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupDetailSessionData;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupDetailSessionDTO(@NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull String location, boolean isCancelled, boolean locationChanged,
        boolean timeChanged, boolean dateChanged, @Nullable Integer attendanceCount) {

    /**
     * Builds a {@link TutorialGroupDetailSessionDTO} from:
     * <ul>
     * <li>data that were derived from a {@link TutorialGroupSession}</li>
     * <li>several fields that represent information about the {@link TutorialGroupSchedule} of the TutorialGroup related to the TutorialGroupSession</li>
     * <li>the time zone of the {@link Course} related to the TutorialGroup of the TutorialGroupSession</li>
     * </ul>
     *
     * @param data              the actual session data
     * @param scheduleDayOfWeek the schedule's day of week (1 = Monday, …, 7 = Sunday)
     * @param scheduleStart     the schedule's start time
     * @param scheduleEnd       the schedule's end time
     * @param scheduleLocation  the schedule's location
     * @param courseTimeZone    the course time zone
     * @return a DTO with session details and flags for deviations
     */
    public static TutorialGroupDetailSessionDTO from(TutorialGroupDetailSessionData data, int scheduleDayOfWeek, LocalTime scheduleStart, LocalTime scheduleEnd,
            String scheduleLocation, ZoneId courseTimeZone) {
        boolean isCancelled = data.status() == TutorialGroupSessionStatus.CANCELLED;
        // if isCancelled is true, set sameLocation, sameTime and sameDay always to true
        boolean sameLocation = isCancelled || data.location().equals(scheduleLocation);
        ZonedDateTime sessionStart = data.start().withZoneSameInstant(courseTimeZone);
        ZonedDateTime sessionEnd = data.end().withZoneSameInstant(courseTimeZone);
        boolean sameTime = isCancelled || sessionStart.toLocalTime().equals(scheduleStart) && sessionEnd.toLocalTime().equals(scheduleEnd);
        boolean sameDay = isCancelled || sessionStart.getDayOfWeek().getValue() == scheduleDayOfWeek;
        return new TutorialGroupDetailSessionDTO(data.start(), data.end(), data.location(), isCancelled, !sameLocation, !sameTime, !sameDay, data.attendanceCount());
    }
}
