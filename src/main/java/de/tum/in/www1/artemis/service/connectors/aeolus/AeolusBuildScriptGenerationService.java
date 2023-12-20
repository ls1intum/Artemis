package de.tum.in.www1.artemis.service.connectors.aeolus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.connectors.BuildScriptGenerationService;
import de.tum.in.www1.artemis.service.connectors.BuildScriptProvider;

@Profile("aeolus")
@Service
public class AeolusBuildScriptGenerationService extends BuildScriptGenerationService {

    private final AeolusBuildPlanService aeolusBuildPlanService;

    private final AeolusTemplateService aeolusTemplateService;

    private final ProfileService profileService;

    public AeolusBuildScriptGenerationService(BuildScriptProvider buildScriptProvider, AeolusBuildPlanService aeolusBuildPlanService, AeolusTemplateService aeolusTemplateService,
            ProfileService profileService) {
        super(buildScriptProvider);
        this.aeolusBuildPlanService = aeolusBuildPlanService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.profileService = profileService;
    }

    @Override
    public String getScript(ProgrammingExercise programmingExercise) {
        if (!profileService.isLocalCi()) {
            return null;
        }
        Windfile windfile = programmingExercise.getWindfile();
        if (windfile == null) {
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
        }
        if (windfile != null) {
            windfile.setId("not-used");
            windfile.setDescription("not-used");
            windfile.setName("not-used");
            return aeolusBuildPlanService.generateBuildScript(windfile, AeolusTarget.CLI);
        }
        return null;
    }
}
