package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.participation.Participation;

public record ParticipationIdDTO(Long id) {

    public ParticipationIdDTO(Participation participation) {
        this(participation.getId());
    }
}
