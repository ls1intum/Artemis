package de.tum.cit.aet.artemis.tutorialgroup.util;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_EMPTY)
public record RawTutorialGroupScheduleDTO(@NotNull Integer dayOfWeek, @NotNull String startTime, @NotNull String endTime, @NotNull String location) {
}
