package de.tum.cit.aet.artemis.programming.service.sharing;

import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * the sharing info, wrapping the original sharing Info from the sharing platform and adding course and exercise info.
 */
@Profile("sharing")
public record SharingSetupInfo(ProgrammingExercise exercise, Course course, SharingInfoDTO sharingInfo) {
}
