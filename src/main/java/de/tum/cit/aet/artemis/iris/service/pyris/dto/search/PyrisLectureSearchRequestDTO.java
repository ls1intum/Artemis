package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchRequestDTO(@NotBlank String query, @Min(1) @Max(20) int limit, @Nullable @JsonProperty("courseIds") List<@NotNull Long> courseIds) {
}
