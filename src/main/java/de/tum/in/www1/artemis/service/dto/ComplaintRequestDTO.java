package de.tum.in.www1.artemis.service.dto;

import java.util.Optional;

import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;

public record ComplaintRequestDTO(long resultId, String complaintText, ComplaintType complaintType, Optional<Long> examId) {
}
