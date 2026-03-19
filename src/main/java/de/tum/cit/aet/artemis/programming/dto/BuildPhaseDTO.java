package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPhaseDTO(String name, String script, BuildPhaseCondition condition, boolean forceRun, List<String> resultPaths) {
}
