package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

public abstract class BuildScriptGenerationService {

    protected final BuildScriptProvider buildScriptProvider;

    public BuildScriptGenerationService(BuildScriptProvider buildScriptProvider) {
        this.buildScriptProvider = buildScriptProvider;
    }

    public abstract String getScript(ProgrammingExercise programmingExercise);

    public abstract String previewScript(Windfile windfile, AeolusTarget target);
}
