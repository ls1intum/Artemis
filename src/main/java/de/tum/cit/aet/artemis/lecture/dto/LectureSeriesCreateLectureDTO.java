package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureSeriesCreateLectureDTO(@NotNull String title, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate) {
}
