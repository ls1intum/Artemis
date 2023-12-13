package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildScriptRepository;

public abstract class BuildScriptGenerationService {

    protected final BuildScriptProvider buildScriptProvider;

    protected final BuildScriptRepository buildScriptRepository;

    public BuildScriptGenerationService(BuildScriptProvider buildScriptProvider, BuildScriptRepository buildScriptRepository) {
        this.buildScriptProvider = buildScriptProvider;
        this.buildScriptRepository = buildScriptRepository;
    }

    public abstract String saveScript(ProgrammingExercise programmingExercise);
}
