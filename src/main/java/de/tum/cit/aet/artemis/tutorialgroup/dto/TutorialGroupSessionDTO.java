package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.Range;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailSessionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupSessionDTO(@NotNull Long id, @NotNull ZonedDateTime start, @NotNull ZonedDateTime end, @NotNull @Size(min = 1, max = 255) String location,
        @NotNull Boolean isCancelled, @NotNull Boolean isCancelledByFreePeriod, @NotNull Boolean locationChanged, @NotNull Boolean timeChanged, @NotNull Boolean dateChanged,
        @Nullable @Range(min = 1, max = 5000) Integer attendanceCount) {

    /**
     * Builds a {@link TutorialGroupSessionDTO} for a {@link TutorialGroupSession} of which the {@link TutorialGroup} has a {@link TutorialGroupSchedule}. Uses:
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
    public static TutorialGroupSessionDTO from(RawTutorialGroupDetailSessionDTO rawDto, int scheduleDayOfWeek, LocalTime scheduleStart, LocalTime scheduleEnd,
            String scheduleLocation, ZoneId courseTimeZone) {
        boolean isCancelled = rawDto.isCancelled();
        boolean isCancelledByFreePeriod = isCancelled && rawDto.isCancelledByFreePeriod();
        boolean sameLocation = rawDto.location().equals(scheduleLocation);
        ZonedDateTime sessionStart = rawDto.start().withZoneSameInstant(courseTimeZone);
        ZonedDateTime sessionEnd = rawDto.end().withZoneSameInstant(courseTimeZone);
        boolean sameTime = sessionStart.toLocalTime().equals(scheduleStart) && sessionEnd.toLocalTime().equals(scheduleEnd);
        boolean sameDay = sessionStart.getDayOfWeek().getValue() == scheduleDayOfWeek;
        return new TutorialGroupSessionDTO(rawDto.id(), rawDto.start(), rawDto.end(), rawDto.location(), isCancelled, isCancelledByFreePeriod, !sameLocation, !sameTime, !sameDay,
                rawDto.attendanceCount());
    }

    /**
     * Builds a {@link TutorialGroupSessionDTO} from data representing properties of a {@link TutorialGroupSession}. This method should be used to construct DTOs for
     * sessions of which the {@link TutorialGroup} has no {@link TutorialGroupSchedule}.
     *
     * @param rawDto data representing properties of the session
     * @return a DTO with session details and flags for schedule deviations
     */
    public static TutorialGroupSessionDTO from(RawTutorialGroupDetailSessionDTO rawDto) {
        boolean isCancelled = rawDto.isCancelled();
        boolean isCancelledByFreePeriod = isCancelled && rawDto.isCancelledByFreePeriod();
        return new TutorialGroupSessionDTO(rawDto.id(), rawDto.start(), rawDto.end(), rawDto.location(), isCancelled, isCancelledByFreePeriod, false, false, false,
                rawDto.attendanceCount());
    }

    /**
     * Builds a {@link TutorialGroupSessionDTO} from a persisted tutorial group session and its optional schedule.
     *
     * @param session  the session entity to convert
     * @param schedule the schedule used to determine deviations, may be {@code null}
     * @return a DTO with session details and flags for schedule deviations
     */
    public static TutorialGroupSessionDTO from(TutorialGroupSession session, TutorialGroupSchedule schedule) {
        boolean isCancelled = session.getStatus() == TutorialGroupSessionStatus.CANCELLED;
        boolean isCancelledByFreePeriod = isCancelled && session.getTutorialGroupFreePeriod() != null;
        boolean sameLocation = true;
        boolean sameTime = true;
        boolean sameDate = true;
        if (schedule != null) {
            LocalTime scheduleStart = LocalTime.parse(schedule.getStartTime());
            LocalTime scheduleEnd = LocalTime.parse(schedule.getEndTime());
            sameLocation = session.getLocation().equals(schedule.getLocation());
            sameTime = session.getStart().toLocalTime().equals(scheduleStart) && session.getEnd().toLocalTime().equals(scheduleEnd);
            sameDate = session.getStart().getDayOfWeek().getValue() == schedule.getDayOfWeek();
        }
        return new TutorialGroupSessionDTO(session.getId(), session.getStart(), session.getEnd(), session.getLocation(), isCancelled, isCancelledByFreePeriod, !sameLocation,
                !sameTime, !sameDate, session.getAttendanceCount());
    }
}
