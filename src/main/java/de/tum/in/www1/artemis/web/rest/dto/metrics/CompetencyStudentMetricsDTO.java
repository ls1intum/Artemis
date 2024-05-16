package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyStudentMetricsDTO(Map<Long, CompetencyInformationDTO> competencyInformation, Map<Long, Set<Long>> exercises, Map<Long, Set<Long>> lectureUnits,
        Map<Long, Double> progress, Map<Long, Double> confidence) {
}
