package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisProgrammingExerciseDTO(long id, String name, ProgrammingLanguage programmingLanguage, Map<String, String> templateRepository,
        Map<String, String> solutionRepository, Map<String, String> testRepository, String problemStatement, Instant startDate, Instant endDate) {

}
