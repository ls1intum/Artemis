package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Routing hint from Pyris indicating the most focused Iris chat context for the answered query.
 * <p>
 * Only present on the done callback when an answer was produced.
 * The frontend uses this to show a "Continue in X chat" button.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PyrisGlobalSearchHandoffDTO(String type, long courseId, @Nullable Long lectureId, @Nullable Long exerciseId) {
}
