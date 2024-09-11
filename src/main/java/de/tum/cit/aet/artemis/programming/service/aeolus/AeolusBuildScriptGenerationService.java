package de.tum.cit.aet.artemis.programming.service.aeolus;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AEOLUS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.AeolusTarget;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.BuildScriptGenerationService;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;

/**
 * Service for generating build scripts for programming exercises using Aeolus
 */
@Profile(PROFILE_AEOLUS)
@Service
public class AeolusBuildScriptGenerationService extends BuildScriptGenerationService {

    private final AeolusBuildPlanService aeolusBuildPlanService;

    private final AeolusTemplateService aeolusTemplateService;

    private final ProfileService profileService;

    /**
     * Instantiates a new build script generation service.
     *
     * @param buildScriptProviderService the build script provider
     * @param aeolusBuildPlanService     the aeolus build plan service
     * @param aeolusTemplateService      the aeolus template service
     * @param profileService             the profile service
     */
    public AeolusBuildScriptGenerationService(BuildScriptProviderService buildScriptProviderService, AeolusBuildPlanService aeolusBuildPlanService,
            AeolusTemplateService aeolusTemplateService, ProfileService profileService) {
        super(buildScriptProviderService);
        this.aeolusBuildPlanService = aeolusBuildPlanService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.profileService = profileService;
    }

    @Override
    public String getScript(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        if (!profileService.isLocalCiActive()) {
            return null;
        }
        Windfile windfile = programmingExercise.getBuildConfig().getWindfile();
        if (windfile == null) {
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
        }
        if (windfile != null) {
            WindfileMetadata oldMetadata = windfile.getMetadata();
            // Creating a new instance of WindfileMetadata with placeholder values for id, name, and description,
            // and copying the rest of the fields from oldMetadata
            WindfileMetadata updatedMetadata = new WindfileMetadata("not-used", "not-used", "not-used", oldMetadata.author(), oldMetadata.gitCredentials(), oldMetadata.docker(),
                    oldMetadata.resultHook(), oldMetadata.resultHookCredentials());
            windfile.setMetadata(updatedMetadata);
            return aeolusBuildPlanService.generateBuildScript(windfile, AeolusTarget.CLI);
        }
        return null;
    }

}
