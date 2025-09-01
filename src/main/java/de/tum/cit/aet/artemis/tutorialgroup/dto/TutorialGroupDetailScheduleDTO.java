package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotNull;

public record TutorialGroupDetailScheduleDTO(@NotNull int dayOfWeek, @NotNull String startTime, @NotNull String endTime, @NotNull String location) {
}
