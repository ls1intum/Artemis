package de.tum.cit.aet.artemis.programming.dto;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.dto.aeolus.AeolusResult;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildPlanPhases(List<BuildPhase> phases, String dockerImage) {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Converts a {@link Windfile} into the {@link BuildPlanPhases} format.
     * Each {@link ScriptAction} in the windfile becomes a {@link BuildPhase} with condition {@link BuildPhaseCondition#ALWAYS}.
     * The docker image is extracted from the windfile metadata.
     *
     * @param windfile the windfile to convert
     * @return the converted {@link BuildPlanPhases}, never null
     */
    public static BuildPlanPhases fromWindfile(Windfile windfile) {
        List<BuildPhase> phases = windfile.scriptActions().stream().map(action -> {
            List<String> resultPaths = action.results() != null ? action.results().stream().map(AeolusResult::path).toList() : Collections.emptyList();
            return new BuildPhase(action.name(), action.script(), BuildPhaseCondition.ALWAYS, resultPaths);
        }).toList();

        String dockerImage = null;
        if (windfile.metadata() != null && windfile.metadata().docker() != null) {
            dockerImage = windfile.metadata().docker().getFullImageName();
        }

        return new BuildPlanPhases(phases, dockerImage);
    }

    public static BuildPlanPhases deserialize(String json) throws JsonProcessingException {
        return mapper.readValue(json, BuildPlanPhases.class);
    }

    public String serialize() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
