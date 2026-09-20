package de.tum.cit.aet.artemis.programming.service.sharing;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;

/**
 * Data wrapper that combines sharing information from the sharing platform with additional context.
 * <p>
 * This is a request body, so it carries the bare {@code @JsonInclude()}: nulls and empty collections must survive on
 * the wire, and the shared architecture rule forbids spelling out {@code Include.ALWAYS}.
 *
 * @param exercise    the programming exercise to be shared or imported
 * @param courseId    the course id of the target course where the exercise will be imported to
 * @param sharingInfo the original sharing information from the sharing platform
 */
@JsonInclude()
public record SharingSetupInfoDTO(@Nullable ImportProgrammingExerciseRequestDTO exercise, long courseId, @Nullable SharingInfoDTO sharingInfo) {
}
