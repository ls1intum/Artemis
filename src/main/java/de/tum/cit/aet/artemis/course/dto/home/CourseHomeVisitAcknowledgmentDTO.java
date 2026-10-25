package de.tum.cit.aet.artemis.course.dto.home;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeVisitAcknowledgmentDTO(@jakarta.validation.constraints.NotNull Instant snapshotAt,
        @jakarta.validation.constraints.PositiveOrZero long observedVariantCursor) {
}
