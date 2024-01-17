package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * Abstract Service for generating build scripts for programming exercises
 */
public abstract class BuildScriptGenerationService {

    protected final BuildScriptProvider buildScriptProvider;

    /**
     * Constructor for BuildScriptGenerationService
     *
     * @param buildScriptProvider buildScriptProvider
     */
    public BuildScriptGenerationService(BuildScriptProvider buildScriptProvider) {
        this.buildScriptProvider = buildScriptProvider;
    }

    /**
     * Should return the file content of the script for the given language and project type with the different options
     *
     * @param programmingExercise the programming exercise for which the build script should be generated
     * @return the script for the given programming exercise
     */
    public abstract String getScript(ProgrammingExercise programmingExercise);
}
