package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSidebarDataDTO(@NotNull String title, long id, @Nullable String moduleNumber, @NotNull ZonedDateTime startDate, int workingTime, int examMaxPoints) {
}
