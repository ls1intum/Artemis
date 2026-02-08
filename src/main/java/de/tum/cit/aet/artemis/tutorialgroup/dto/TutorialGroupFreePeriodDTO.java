package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(@NotNull Long id, @NotNull String start, @NotNull String end, String reason,
        @NotNull TutorialGroupConfigurationDTO tutorialGroupsConfiguration) {

    /**
     * Creates a {@link TutorialGroupFreePeriodDTO} from a {@link TutorialGroupFreePeriod} entity.
     *
     * @param freePeriod the tutorial group free period entity
     * @return a DTO representing the given free period
     */
    public static TutorialGroupFreePeriodDTO of(@NotNull TutorialGroupFreePeriod freePeriod) {
        var systemZone = ZoneId.systemDefault();

        String start = freePeriod.getStart().withZoneSameInstant(systemZone).toOffsetDateTime().toString();

        String end = freePeriod.getEnd().withZoneSameInstant(systemZone).toOffsetDateTime().toString();

        return new TutorialGroupFreePeriodDTO(freePeriod.getId(), start, end, freePeriod.getReason(),
                TutorialGroupConfigurationDTO.of(freePeriod.getTutorialGroupsConfiguration()));
    }
}
