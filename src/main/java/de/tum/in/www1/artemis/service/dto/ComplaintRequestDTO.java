package de.tum.in.www1.artemis.service.dto;

import java.util.OptionalLong;

import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;

public record ComplaintRequestDTO(Long resultId, String complaintText, ComplaintType complaintType, OptionalLong examId) {
}
