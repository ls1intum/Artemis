package de.tum.cit.aet.artemis.service.connectors;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseBuildConfig;

/**
 * Service for generating build scripts for programming exercises
 */
@Profile("!aeolus & localci")
@Service
public class GenericBuildScriptGenerationService extends BuildScriptGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenericBuildScriptGenerationService.class);

    /**
     * Constructor for BuildScriptGenerationService
     *
     * @param buildScriptProviderService buildScriptProvider
     */
    public GenericBuildScriptGenerationService(BuildScriptProviderService buildScriptProviderService) {
        super(buildScriptProviderService);
    }

    @Override
    public String getScript(ProgrammingExercise programmingExercise) {
        try {
            ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
            return buildScriptProviderService.getScriptFor(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()),
                    programmingExercise.isStaticCodeAnalysisEnabled(), buildConfig.hasSequentialTestRuns(), buildConfig.isTestwiseCoverageEnabled());
        }
        catch (IOException e) {
            log.error("Failed to generate build script for programming exercise " + programmingExercise.getId(), e);
        }
        return null;
    }
}
