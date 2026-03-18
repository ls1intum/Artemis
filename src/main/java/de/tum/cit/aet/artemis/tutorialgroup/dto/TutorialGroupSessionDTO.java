package de.tum.cit.aet.artemis.tutorialgroup.dto;

import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * DTO representing a tutorial group session, contains all relevant information about a tutorial group session, including its schedule and free period if applicable.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupSessionDTO(@NotNull Long id, @NotNull LocalDateTime start, @NotNull LocalDateTime end, TutorialGroupSessionStatus status,
        @Size(min = 1, max = 256) String statusExplanation, @NotNull @Size(max = 2000) String location, @Max(3000) Integer attendanceCount, TutorialGroupScheduleDTO schedule,
        TutorialGroupFreePeriodDTO freePeriod) {

    /**
     * DTO representing the tutorial group schedule a session originates from.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupScheduleDTO(@NotNull Long id, @NotNull Integer dayOfWeek, @NotNull String startTime, @NotNull String endTime, @NotNull Integer repetitionFrequency) {

        public static TutorialGroupScheduleDTO of(TutorialGroupSchedule schedule) {
            Objects.requireNonNull(schedule, "Tutorial group schedule must be set");
            return new TutorialGroupScheduleDTO(schedule.getId(), schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(), schedule.getRepetitionFrequency());
        }

    }

    /**
     * DTO used to send the status explanation when e.g., cancelling a tutorial group session
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupStatusDTO(@Size(min = 1, max = 256) String statusExplanation) {
    }

    /**
     * DTO used because we want to interpret the dates in the time zone of the tutorial groups configuration
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSessionRequestDTO(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotNull @Size(max = 2000) String location) {

        /**
         * Converts this DTO to a {@link TutorialGroupSession} entity, interpreting the date and time fields in the time zone of the given tutorial groups configuration.
         *
         * @param tutorialGroupSessionRequestDTO the DTO to convert
         * @param tutorialGroupsConfiguration    the tutorial groups configuration to use for the conversion (needed for the time zone)
         * @param tutorialGroup                  the tutorial group to which the session belongs (needed to set the relationship)
         * @return the converted TutorialGroupSession object
         */
        public static TutorialGroupSession createFromDto(TutorialGroupSessionRequestDTO tutorialGroupSessionRequestDTO, TutorialGroupsConfiguration tutorialGroupsConfiguration,
                TutorialGroup tutorialGroup) {
            Objects.requireNonNull(tutorialGroupSessionRequestDTO, "Tutorial group session request DTO must be set");
            Objects.requireNonNull(tutorialGroupsConfiguration, "Tutorial groups configuration must be set");
            String timeZone = tutorialGroupsConfiguration.getCourse().getTimeZone();
            TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
            tutorialGroupSession.setStart(interpretInTimeZone(tutorialGroupSessionRequestDTO.date(), tutorialGroupSessionRequestDTO.startTime(), timeZone));
            tutorialGroupSession.setEnd(interpretInTimeZone(tutorialGroupSessionRequestDTO.date(), tutorialGroupSessionRequestDTO.endTime(), timeZone));
            tutorialGroupSession.setLocation(tutorialGroupSessionRequestDTO.location());
            tutorialGroupSession.setTutorialGroup(tutorialGroup);
            return tutorialGroupSession;
        }

    }

    /**
     * Creates a {@link TutorialGroupSessionDTO} from a {@link TutorialGroupSession} entity.
     * Interprets stored UTC timestamps (ZonedDateTime) in the course time zone and converts them to LocalDateTime.
     *
     * @param session    the tutorial group session entity
     * @param courseZone the course time zone (ZoneId.of(course.getTimeZone()))
     * @return a DTO representing the given session
     */
    public static TutorialGroupSessionDTO of(TutorialGroupSession session, ZoneId courseZone) {
        Objects.requireNonNull(session, "Tutorial group session must be set");
        Objects.requireNonNull(courseZone, "Course time zone must be set");
        Objects.requireNonNull(session.getStart(), "Tutorial group session start time must be set");
        Objects.requireNonNull(session.getEnd(), "Tutorial group session end time must be set");
        if (session.getStart().isAfter(session.getEnd())) {
            throw new IllegalStateException("The session start date must be before the end date.");
        }
        LocalDateTime start = session.getStart().withZoneSameInstant(courseZone).toLocalDateTime();
        LocalDateTime end = session.getEnd().withZoneSameInstant(courseZone).toLocalDateTime();

        var scheduleDto = session.getTutorialGroupSchedule() != null ? TutorialGroupScheduleDTO.of(session.getTutorialGroupSchedule()) : null;
        var freePeriodDto = session.getTutorialGroupFreePeriod() != null ? TutorialGroupFreePeriodDTO.of(session.getTutorialGroupFreePeriod(), courseZone) : null;

        return new TutorialGroupSessionDTO(session.getId(), start, end, session.getStatus(), session.getStatusExplanation(), session.getLocation(), session.getAttendanceCount(),
                scheduleDto, freePeriodDto);
    }
}
