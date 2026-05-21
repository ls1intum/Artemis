package de.tum.cit.aet.artemis.tutorialgroup.dto;

import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

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
     * @param tutorialGroupsConfiguration the tutorial groups configuration to use for the conversion (needed for the time zone)
     * @return the converted TutorialGroupSession object
     */
    public TutorialGroupSession toEntity(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
        tutorialGroupSession.setStart(interpretInTimeZone(date, startTime, tutorialGroupsConfiguration.getCourse().getTimeZone()));
        tutorialGroupSession.setEnd(interpretInTimeZone(date, endTime, tutorialGroupsConfiguration.getCourse().getTimeZone()));
        tutorialGroupSession.setLocation(location);
        tutorialGroupSession.setAttendanceCount(attendance);
        return tutorialGroupSession;
    }

}
