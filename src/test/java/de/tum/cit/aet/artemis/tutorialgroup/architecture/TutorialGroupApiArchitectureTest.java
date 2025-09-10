package de.tum.cit.aet.artemis.tutorialgroup.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupApiNotPresentException;

class TutorialGroupApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".tutorialgroup";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(TutorialGroupApiNotPresentException.class);
    }
}
