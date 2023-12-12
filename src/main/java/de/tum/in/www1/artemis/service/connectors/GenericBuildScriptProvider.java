package de.tum.in.www1.artemis.service.connectors;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.BuildScriptRepository;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

@Component
public class GenericBuildScriptProvider {

    protected final BuildScriptRepository buildScriptRepository;

    protected final AeolusTemplateService aeolusTemplateService;

    public GenericBuildScriptProvider(BuildScriptRepository buildScriptRepository, AeolusTemplateService aeolusTemplateService) {
        this.buildScriptRepository = buildScriptRepository;
        this.aeolusTemplateService = aeolusTemplateService;
    }

    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();

    public String getScriptFor(ProgrammingExercise programmingExercise, boolean serveTemplate) {
        var buildScript = buildScriptRepository.findByProgrammingExercisesId(programmingExercise.getId());
        if (buildScript.isPresent()) {
            return buildScript.get().getBuildScript();
        }
        if (serveTemplate) {
            Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
            if (windfile != null) {
                return buildFromWindfile(windfile);
            }
        }
        return null;
    }

    public String getScriptFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage)
            throws IOException {
        String uniqueKey = programmingLanguage.name().toLowerCase() + "_" + projectType.map(ProjectType::name).orElse("none") + "_" + staticAnalysis + "_" + sequentialRuns + "_"
                + testCoverage;
        if (scriptCache.containsKey(uniqueKey)) {
            return scriptCache.get(uniqueKey);
        }
        Windfile windfile = aeolusTemplateService.getWindfileFor(programmingLanguage, projectType, staticAnalysis, sequentialRuns, testCoverage);
        if (windfile != null) {
            String script = buildFromWindfile(windfile);
            scriptCache.put(uniqueKey, script);
            return script;
        }
        return null;
    }

    private String buildFromWindfile(Windfile windfile) {
        StringBuilder buildScript = new StringBuilder();
        windfile.getScriptActions().forEach(action -> {
            String workdir = action.getWorkdir();
            if (workdir != null) {
                buildScript.append("cd ").append(workdir).append("\n");
            }
            buildScript.append(action.getScript()).append("\n");
            if (workdir != null) {
                buildScript.append("cd ").append(workdir).append("\n");
            }
        });
        return buildScript.toString();
    }
}
