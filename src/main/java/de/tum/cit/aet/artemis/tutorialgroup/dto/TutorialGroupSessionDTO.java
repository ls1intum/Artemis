package de.tum.cit.aet.artemis.tutorialgroup.dto;

import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * DTO representing a tutorial group session, contains all relevant information about a tutorial group session, including its schedule and free period if applicable.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupSessionDTO(@NotNull Long id, @NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate, boolean isCancelled, String statusExplanation,
        @NotNull String location, Integer attendanceCount, TutorialGroupScheduleDTO schedule, TutorialGroupFreePeriodDTO freePeriod) {

    private static final String ENTITY_NAME = "tutorialGroupSession";

    /**
     * DTO representing the tutorial group schedule a session originates from.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupScheduleDTO(@NotNull Long id, Integer dayOfWeek, String startTime, String endTime, Integer repetitionFrequency) {

        public static TutorialGroupScheduleDTO of(@NotNull TutorialGroupSchedule schedule) {
            return new TutorialGroupScheduleDTO(schedule.getId(), schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(), schedule.getRepetitionFrequency());
        }

    }

    /**
     * DTO used to send the status explanation when i.g. cancelling a tutorial group session
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupStatusDTO(String statusExplanation) {
    }

    /**
     * DTO used because we want to interpret the dates in the time zone of the tutorial groups configuration
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSessionRequestDTO(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime, @Size(min = 1, max = 2000) String location) {

        public void validityCheck() {
            if (!startTime.isBefore(endTime)) {
                throw new BadRequestAlertException("The start time must be before the end time", ENTITY_NAME, "startTimeAfterEndTime");
            }
        }

        /**
         * Convert the DTO to a TutorialGroupSession object
         *
         * @param tutorialGroupsConfiguration the tutorial groups configuration to use for the conversion (needed for the time zone)
         * @return the converted TutorialGroupSession object
         */
        public TutorialGroupSession toEntity(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
            TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
            tutorialGroupSession.setStart(interpretInTimeZone(date, startTime, tutorialGroupsConfiguration.getCourse().getTimeZone()));
            tutorialGroupSession.setEnd(interpretInTimeZone(date, endTime, tutorialGroupsConfiguration.getCourse().getTimeZone()));
            tutorialGroupSession.setLocation(location);
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
    public static TutorialGroupSessionDTO of(@NotNull TutorialGroupSession session, @NotNull ZoneId courseZone) {
        if (session.getStart() == null || session.getEnd() == null) {
            throw new IllegalStateException("Tutorial group session has no start or end date");
        }

        LocalDateTime start = session.getStart().withZoneSameInstant(courseZone).toLocalDateTime();
        LocalDateTime end = session.getEnd().withZoneSameInstant(courseZone).toLocalDateTime();

        var scheduleDto = session.getTutorialGroupSchedule() != null ? TutorialGroupScheduleDTO.of(session.getTutorialGroupSchedule()) : null;

        // If you want to avoid large nested objects or cycles, use a slim free period DTO or ignore nested config there.
        var freePeriodDto = session.getTutorialGroupFreePeriod() != null ? TutorialGroupFreePeriodDTO.of(session.getTutorialGroupFreePeriod()) : null;
        boolean isCancelled = session.getStatus() == TutorialGroupSessionStatus.CANCELLED;

        return new TutorialGroupSessionDTO(session.getId(), start, end, isCancelled, session.getStatusExplanation(), session.getLocation(), session.getAttendanceCount(),
                scheduleDto, freePeriodDto);
    }

}
