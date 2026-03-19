package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPhaseDTO(@NotBlank @Pattern(regexp = BuildPhaseDTO.BUILD_PHASE_NAME_REGEX) String name, String script, BuildPhaseCondition condition, boolean forceRun,
        List<String> resultPaths) {

    public static final String BUILD_PHASE_NAME_REGEX = "^[A-Za-z0-9_-]+$";

    public static final java.util.regex.Pattern BUILD_PHASE_NAME_PATTERN = java.util.regex.Pattern.compile(BUILD_PHASE_NAME_REGEX);

}
