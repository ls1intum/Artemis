package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSidebarDataDTO(@NotNull String title, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, @Nullable String moduleNumber,
        @NotNull ZonedDateTime startDate, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int workingTime, int examMaxPoints) {
}
