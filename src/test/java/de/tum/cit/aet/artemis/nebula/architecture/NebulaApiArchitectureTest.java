package de.tum.cit.aet.artemis.nebula.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class NebulaApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        // @formatter:off
        return Set.of(
        );
        // @formatter:on
    }
}
