package de.tum.cit.aet.artemis.hyperion.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class HyperionApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".hyperion";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of();
    }
}
