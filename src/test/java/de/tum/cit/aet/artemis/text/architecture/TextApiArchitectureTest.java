package de.tum.cit.aet.artemis.text.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;
import de.tum.cit.aet.artemis.text.config.TextExerciseNotPresentException;

class TextApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".text";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(TextExerciseNotPresentException.class);
    }
}
