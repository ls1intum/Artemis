package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComplaintResponseUpdateDTO(String responseText, Boolean complaintIsAccepted, ComplaintAction action) {
}
