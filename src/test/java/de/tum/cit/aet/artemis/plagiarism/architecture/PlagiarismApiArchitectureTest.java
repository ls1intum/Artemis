package de.tum.cit.aet.artemis.plagiarism.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.modeling.web.ModelingExerciseResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;
import de.tum.cit.aet.artemis.text.web.TextExerciseResource;

class PlagiarismApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".plagiarism";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(ModelingExerciseResource.class, TextExerciseResource.class);
    }
}
