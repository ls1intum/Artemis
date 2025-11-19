package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailSessionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupDetailSessionDTO(@NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull String location, boolean isCancelled, boolean locationChanged,
        boolean timeChanged, boolean dateChanged, @Nullable Integer attendanceCount) {

    /**
     * Builds a {@link TutorialGroupDetailSessionDTO} for a {@link TutorialGroupSession} of which the {@link TutorialGroup} has a {@link TutorialGroupSchedule}. Uses:
     * <ul>
     * <li>data representing properties of the session</li>
     * <li>several fields that represent information about the {@link TutorialGroupSchedule}</li>
     * <li>the time zone of the {@link Course} related to the TutorialGroup of the TutorialGroupSession</li>
     * </ul>
     *
     * @param rawDto            data representing properties of the session
     * @param scheduleDayOfWeek the schedule's day of week (1 = Monday, …, 7 = Sunday)
     * @param scheduleStart     the schedule's start time
     * @param scheduleEnd       the schedule's end time
     * @param scheduleLocation  the schedule's location
     * @param courseTimeZone    the course time zone
     * @return a DTO with session details and flags for deviations
     */
    public static TutorialGroupDetailSessionDTO from(RawTutorialGroupDetailSessionDTO rawDto, int scheduleDayOfWeek, LocalTime scheduleStart, LocalTime scheduleEnd,
            String scheduleLocation, ZoneId courseTimeZone) {
        boolean isCancelled = rawDto.status() == TutorialGroupSessionStatus.CANCELLED;
        // if isCancelled is true, set sameLocation, sameTime and sameDay always to true
        boolean sameLocation = isCancelled || rawDto.location().equals(scheduleLocation);
        ZonedDateTime sessionStart = rawDto.start().withZoneSameInstant(courseTimeZone);
        ZonedDateTime sessionEnd = rawDto.end().withZoneSameInstant(courseTimeZone);
        boolean sameTime = isCancelled || sessionStart.toLocalTime().equals(scheduleStart) && sessionEnd.toLocalTime().equals(scheduleEnd);
        boolean sameDay = isCancelled || sessionStart.getDayOfWeek().getValue() == scheduleDayOfWeek;
        return new TutorialGroupDetailSessionDTO(rawDto.start(), rawDto.end(), rawDto.location(), isCancelled, !sameLocation, !sameTime, !sameDay, rawDto.attendanceCount());
    }

    /**
     * Builds a {@link TutorialGroupDetailSessionDTO} from data representing properties of a {@link TutorialGroupSession}. This method should be used to construct DTOs for
     * sessions of which the {@link TutorialGroup} has no {@link TutorialGroupSchedule}.
     *
     * @param rawDto data representing properties of the session
     * @return a DTO with session details and flags for schedule deviations
     */
    public static TutorialGroupDetailSessionDTO from(RawTutorialGroupDetailSessionDTO rawDto) {
        boolean isCancelled = rawDto.status() == TutorialGroupSessionStatus.CANCELLED;
        return new TutorialGroupDetailSessionDTO(rawDto.start(), rawDto.end(), rawDto.location(), isCancelled, false, false, false, rawDto.attendanceCount());
    }
}
