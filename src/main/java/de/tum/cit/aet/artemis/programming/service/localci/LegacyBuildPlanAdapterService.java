package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LegacyBuildPlanAdapterService {

    private final ObjectMapper objectMapper;

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    public LegacyBuildPlanAdapterService(ObjectMapper objectMapper, final BuildPhasesTemplateService buildPhasesTemplateService) {
        this.objectMapper = objectMapper;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
    }

    public String extractLegacyDockerImage(ProgrammingExercise programmingExercise) {
        String buildPlanConfiguration = programmingExercise.getBuildConfig().getBuildPlanConfiguration();
        if (buildPlanConfiguration == null || buildPlanConfiguration.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(buildPlanConfiguration);
            JsonNode imageNode = node.path("metadata").path("docker").path("image");
            if (imageNode.isTextual()) {
                String image = imageNode.asText().trim();
                if (!image.isBlank()) {
                    return image;
                }
            }
        }
        catch (Exception e) {
            return null;
        }
        return null;
    }

    public List<BuildPhaseDTO> createBuildPhasesFromLegacyBuildScript(ProgrammingExercise programmingExercise) {
        List<BuildPhaseDTO> templatePhases = buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(programmingExercise);
        List<String> resultPaths = BuildPhaseEvaluationService.gatherResultPaths(templatePhases).stream().toList();
        String legacyScript = programmingExercise.getBuildConfig().getBuildScript();
        String wrappedScript = "cd " + LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir\n" + "  local tmp_file=$(mktemp)\n"
                + "cat << '__LEGACY_INNER_SCRIPT_END__' > \"${tmp_file}\"\n" + legacyScript + "\n" + "__LEGACY_INNER_SCRIPT_END__\n" + "  chmod +x \"${tmp_file}\"\n"
                + "  \"${tmp_file}\" \"$@\"\n";
        return List.of(new BuildPhaseDTO("script", wrappedScript, BuildPhaseCondition.ALWAYS, false, resultPaths));
    }
}
