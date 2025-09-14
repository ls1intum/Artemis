package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

public record TutorialGroupDetailGroupDTOMetaData(long courseId, int scheduleDayOfWeek, @NotNull String scheduleStartTime, @NotNull String scheduleEndTime,
        @NotNull String scheduleLocation) {
}
