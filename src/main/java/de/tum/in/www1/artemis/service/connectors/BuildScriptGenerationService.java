package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public abstract class BuildScriptGenerationService {

    protected final BuildScriptProvider buildScriptProvider;

    public BuildScriptGenerationService(BuildScriptProvider buildScriptProvider) {
        this.buildScriptProvider = buildScriptProvider;
    }

    public abstract String saveScript(ProgrammingExercise programmingExercise);
}
