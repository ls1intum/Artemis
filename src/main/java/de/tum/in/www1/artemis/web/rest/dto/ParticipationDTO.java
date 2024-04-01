package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.participation.Participation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationDTO(Long id, boolean testRun, String type, Integer submissionCount) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static ParticipationDTO of(Participation participation) {
        return new ParticipationDTO(participation.getId(), participation.isTestRun(), participation.getType(), participation.getSubmissionCount());
    }
}
