package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComplaintRequestDTO(long resultId, String complaintText, ComplaintType complaintType, Optional<Long> examId) {
}
