package de.tum.cit.aet.artemis.programming.service.sharing;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Data wrapper that combines sharing information from the sharing platform with additional context.
 *
 * @param exercise    the programming exercise to be shared or imported
 * @param courseId    the course id of the target course where the exercise will be imported to
 * @param sharingInfo the original sharing information from the sharing platform
 */
// TODO: we should use a proper DTO for exercise here
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SharingSetupInfoDTO(@NonNull ProgrammingExercise exercise, long courseId, @NonNull SharingInfoDTO sharingInfo) {
}
