package de.tum.in.www1.artemis.service.connectors.hades;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractBuildPlanCreator;

// TODO: to we need this service? We never use a build plan for hades
@Profile("hades")
@Service
public class HadesBuildPlanService extends AbstractBuildPlanCreator {

    @Value("artemis.hades.url")
    private String hadesServerUrl;

    @Value("artemis.hades.token")
    private String hadesToken;

    protected HadesBuildPlanService(BuildPlanRepository buildPlanRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        super(buildPlanRepository, programmingExerciseRepository);
    }

    @Override
    protected String generateDefaultBuildPlan(ProgrammingExercise programmingExercise) {
        // TODO: add buildscript here
        return "echo 'Hello World'";
    }
}
