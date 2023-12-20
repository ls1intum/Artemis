package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * Abstract Service for generating build scripts for programming exercises
 */
public abstract class BuildScriptGenerationService {

    protected final BuildScriptProvider buildScriptProvider;

    public BuildScriptGenerationService(BuildScriptProvider buildScriptProvider) {
        this.buildScriptProvider = buildScriptProvider;
    }

    public abstract String getScript(ProgrammingExercise programmingExercise);
}
