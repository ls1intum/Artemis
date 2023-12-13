package de.tum.in.www1.artemis.service.connectors.aeolus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BuildScript;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.repository.BuildScriptRepository;
import de.tum.in.www1.artemis.service.connectors.BuildScriptGenerationService;
import de.tum.in.www1.artemis.service.connectors.BuildScriptProvider;

@Profile("aeolus")
@Service
public class AeolusBuildScriptGenerationService extends BuildScriptGenerationService {

    private final AeolusBuildPlanService aeolusBuildPlanService;

    private final AeolusTemplateService aeolusTemplateService;

    public AeolusBuildScriptGenerationService(BuildScriptProvider buildScriptProvider, BuildScriptRepository buildScriptRepository, AeolusBuildPlanService aeolusBuildPlanService,
            AeolusTemplateService aeolusTemplateService) {
        super(buildScriptProvider, buildScriptRepository);
        this.aeolusBuildPlanService = aeolusBuildPlanService;
        this.aeolusTemplateService = aeolusTemplateService;
    }

    @Override
    public String saveScript(ProgrammingExercise programmingExercise) {
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
}
