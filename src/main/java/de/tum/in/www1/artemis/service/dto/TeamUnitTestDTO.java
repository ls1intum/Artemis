package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.artemis.domain.Exercise;

public class TeamUnitTestDTO {

    @JsonProperty(access = JsonProperty.Access.READ_WRITE)
    private Exercise exercise;
}
