package de.tum.in.www1.artemis.service.dto;

public record ComplaintResponseUpdateDTO(String responseText, Boolean complaintIsAccepted, ComplaintAction action) {
}
