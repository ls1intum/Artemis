package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.participation.Participation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationIdDTO(Long id) {

    public ParticipationIdDTO(Participation participation) {
        this(participation.getId());
    }
}
