package de.tum.cit.aet.artemis.course.dto.home;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeQueryDTO(@Nullable Instant since) {
}
