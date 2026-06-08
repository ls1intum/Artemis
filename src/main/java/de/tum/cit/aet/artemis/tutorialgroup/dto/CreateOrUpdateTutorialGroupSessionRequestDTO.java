package de.tum.cit.aet.artemis.tutorialgroup.dto;

import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * DTO used because we want to interpret the dates in the time zone of the tutorial groups configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateOrUpdateTutorialGroupSessionRequestDTO(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
        @NotNull @Size(min = 1, max = 2000) String location, @Nullable Integer attendance) {

    public void validityCheck() {
        if (!startTime.isBefore(endTime)) {
            throw new BadRequestAlertException("The start time must be before the end time", "tutorialGroupSession", "startTimeAfterEndTime");
        }
    }

    /**
     * Convert the DTO to a TutorialGroupSession object
     *
     * @param courseTimeZone the course time zone to use for the conversion
     * @return the converted TutorialGroupSession object
     */
    public TutorialGroupSession toEntity(ZoneId courseTimeZone) {
        TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
        tutorialGroupSession.setStart(interpretInTimeZone(date, startTime, courseTimeZone.getId()));
        tutorialGroupSession.setEnd(interpretInTimeZone(date, endTime, courseTimeZone.getId()));
        tutorialGroupSession.setLocation(location);
        tutorialGroupSession.setAttendanceCount(attendance);
        return tutorialGroupSession;
    }

}
