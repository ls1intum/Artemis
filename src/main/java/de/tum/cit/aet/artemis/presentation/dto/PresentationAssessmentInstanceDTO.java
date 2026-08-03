package de.tum.cit.aet.artemis.presentation.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentMode;

/**
 * DTO for a scheduled presentation assessment instance.
 */
public record PresentationAssessmentInstanceDTO(Long id, @NotNull ZonedDateTime presentationDate, @PositiveOrZero Double resultPoints, List<String> studentLogins,
        @Size(max = 10) String language, PresentationAssessmentMode mode, @Size(max = 255) String location, @Size(max = 1000) String meetingLink) {

    public static PresentationAssessmentInstanceDTO of(PresentationAssessmentInstance instance) {
        return new PresentationAssessmentInstanceDTO(instance.getId(), instance.getPresentationDate(), instance.getResultPoints(),
                instance.getStudents().stream().map(student -> student.getLogin()).sorted().toList(), instance.getLanguage(), instance.getMode(), instance.getLocation(),
                instance.getMeetingLink());
    }
}
