package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisProgrammingExerciseDTO(
        long id,
        String name,
        ProgrammingLanguage programmingLanguage,
        Map<String, String> templateRepository,
        Map<String, String> solutionRepository,
        Map<String, String> testRepository,
        String problemStatement,
        Instant startDate,
        Instant endDate
) {}
// @formatter:off
