package de.tum.cit.aet.artemis.programming.service.sharing;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Data wrapper that combines sharing information from the sharing platform with additional context.
 *
 * @param exercise    the programming exercise to be shared or imported
 * @param course      the course context for the exercise
 * @param sharingInfo the original sharing information from the sharing platform
 */
public record SharingSetupInfo(@NonNull ProgrammingExercise exercise, @NonNull Course course, @NonNull SharingInfoDTO sharingInfo) {
}
