package de.tum.cit.aet.artemis.programming.dto;

import java.util.Collections;
import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.aeolus.AeolusResult;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;

@JsonInclude()
public record BuildPlanPhasesDTO(List<@Valid BuildPhaseDTO> phases, String dockerImage) {

    private static final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    /**
     * Converts a {@link Windfile} into the {@link BuildPlanPhasesDTO} format.
     * Each {@link ScriptAction} in the windfile becomes a {@link BuildPhaseDTO} with condition {@link BuildPhaseCondition#ALWAYS}.
     * The docker image is extracted from the windfile metadata.
     *
     * @param windfile the windfile to convert
     * @return the converted {@link BuildPlanPhasesDTO}, never null
     */
    public static BuildPlanPhasesDTO fromWindfile(Windfile windfile) {
        List<BuildPhaseDTO> phases = windfile.scriptActions().stream().map(action -> {
            final List<String> resultPaths = action.results() != null ? action.results().stream().map(AeolusResult::path).toList() : Collections.emptyList();
            final String script = prependWorkdir(action.script(), action.workdir());
            return new BuildPhaseDTO(action.name(), script, BuildPhaseCondition.ALWAYS, action.runAlways(), resultPaths);
        }).toList();

        String dockerImage = null;
        if (windfile.metadata() != null && windfile.metadata().docker() != null) {
            dockerImage = windfile.metadata().docker().getFullImageName();
        }

        return new BuildPlanPhasesDTO(phases, dockerImage);
    }

    private static String prependWorkdir(String script, String workdir) {
        if (workdir == null || workdir.isBlank()) {
            return script;
        }

        return "cd \"" + workdir + "\"\n" + script;
    }

    public static BuildPlanPhasesDTO fromBuildPlanConfiguration(String buildPlanConfiguration) throws JsonProcessingException {
        return mapper.readValue(buildPlanConfiguration, BuildPlanPhasesDTO.class);
    }

    public String toBuildPlanConfiguration() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
