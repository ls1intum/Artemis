package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

public record TutorialGroupDetailGroupDTOMetaData(@NotNull Long courseId, @NotNull int scheduleDayOfWeek, @NotNull String scheduleStartTime, @NotNull String scheduleEndTime,
        @NotNull String scheduleLocation) {
}
