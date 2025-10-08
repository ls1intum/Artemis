package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureNameUpdateDTO(@NotNull Long id, @NotNull String title) {
}
