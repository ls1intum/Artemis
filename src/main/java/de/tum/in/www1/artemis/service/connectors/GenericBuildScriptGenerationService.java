package de.tum.in.www1.artemis.service.connectors;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildScriptRepository;

@Profile("!aeolus")
@Service
public class GenericBuildScriptGenerationService extends BuildScriptGenerationService {

    private final Logger LOGGER = LoggerFactory.getLogger(BuildScriptProvider.class);

    public GenericBuildScriptGenerationService(BuildScriptProvider buildScriptProvider, BuildScriptRepository buildScriptRepository) {
        super(buildScriptProvider, buildScriptRepository);
    }

    @Override
    public String saveScript(ProgrammingExercise programmingExercise) {
        try {
            return buildScriptProvider.getTemplateScriptFor(programmingExercise);
        }
        catch (IOException e) {
            LOGGER.error("Could not find build script for exercise " + programmingExercise.getId() + " due to " + e.getMessage());
        }
        return null;
    }
}
