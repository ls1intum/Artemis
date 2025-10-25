package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureSeriesCreateLectureDTO(@NotNull String title, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate) {
}
