package de.tum.cit.aet.artemis.programming.dto;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.aeolus.AeolusResult;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPlanPhasesDTO(List<BuildPhaseDTO> phases, String dockerImage) {

    private static final ObjectMapper mapper = new ObjectMapper();

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
            List<String> resultPaths = action.results() != null ? action.results().stream().map(AeolusResult::path).toList() : Collections.emptyList();
            return new BuildPhaseDTO(action.name(), action.script(), BuildPhaseCondition.ALWAYS, resultPaths);
        }).toList();

        String dockerImage = null;
        if (windfile.metadata() != null && windfile.metadata().docker() != null) {
            dockerImage = windfile.metadata().docker().getFullImageName();
        }

        return new BuildPlanPhasesDTO(phases, dockerImage);
    }

    public static BuildPlanPhasesDTO deserialize(String json) throws JsonProcessingException {
        return mapper.readValue(json, BuildPlanPhasesDTO.class);
    }

    public String serialize() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
