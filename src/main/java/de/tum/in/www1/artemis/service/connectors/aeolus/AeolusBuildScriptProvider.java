package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.io.IOException;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.BuildScript;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.BuildScriptRepository;
import de.tum.in.www1.artemis.service.connectors.GenericBuildScriptProvider;

@Profile("aeolus")
@Component
public class AeolusBuildScriptProvider extends GenericBuildScriptProvider {

    private final AeolusBuildPlanService aeolusBuildPlanService;

    public AeolusBuildScriptProvider(BuildScriptRepository buildScriptRepository, AeolusTemplateService aeolusTemplateService, AeolusBuildPlanService aeolusBuildPlanService) {
        super(buildScriptRepository, aeolusTemplateService);
        this.aeolusBuildPlanService = aeolusBuildPlanService;
    }

    public String getScriptFor(ProgrammingExercise programmingExercise) {
        String existingScript = super.getScriptFor(programmingExercise, false);
        if (existingScript != null) {
            return existingScript;
        }
        Windfile windfile = programmingExercise.getWindfile();
        if (windfile == null) {
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
        }
        if (windfile != null) {
            String script = aeolusBuildPlanService.generateBuildScript(windfile, AeolusTarget.CLI);
            BuildScript buildScript = new BuildScript();
            buildScript.setBuildScript(script);
            buildScript.setProgrammingExercise(programmingExercise);
            buildScriptRepository.save(buildScript);
            return script;
        }
        return null;
    }

    public String getScriptFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage)
            throws IOException {
        Windfile windfile = this.aeolusTemplateService.getWindfileFor(programmingLanguage, projectType, staticAnalysis, sequentialRuns, testCoverage);
        if (windfile != null) {
            return aeolusBuildPlanService.generateBuildScript(windfile, AeolusTarget.CLI);
        }
        return null;
    }
}
