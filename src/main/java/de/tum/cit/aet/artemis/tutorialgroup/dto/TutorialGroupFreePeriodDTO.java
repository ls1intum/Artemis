package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(@NotNull Long id, @NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate, String reason,
        @NotNull TutorialGroupConfigurationDTO tutorialGroupsConfiguration) {

    /**
     * Creates a {@link TutorialGroupFreePeriodDTO} from a {@link TutorialGroupFreePeriod} entity.
     *
     * @param freePeriod the tutorial group free period entity
     * @return a DTO representing the given free period
     */
    public static TutorialGroupFreePeriodDTO of(@NotNull TutorialGroupFreePeriod freePeriod) {
        if (freePeriod.getTutorialGroupsConfiguration() == null) {
            throw new IllegalStateException("Tutorial group free period is not associated with a tutorial group configuration");
        }
        var course = freePeriod.getTutorialGroupsConfiguration().getCourse();
        if (course == null || course.getTimeZone() == null) {
            throw new IllegalStateException("Tutorial group configuration is associated with a course without a time zone");
        }
        ZoneId courseZone = ZoneId.of(course.getTimeZone());
        LocalDateTime startDate = freePeriod.getStart().withZoneSameInstant(courseZone).toLocalDateTime();
        LocalDateTime endDate = freePeriod.getEnd().withZoneSameInstant(courseZone).toLocalDateTime();

        return new TutorialGroupFreePeriodDTO(freePeriod.getId(), startDate, endDate, freePeriod.getReason(),
                TutorialGroupConfigurationDTO.of(freePeriod.getTutorialGroupsConfiguration()));
    }
}
