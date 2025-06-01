package de.tum.cit.aet.artemis.atlas.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.atlas.config.AtlasNotPresentException;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class AtlasApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".atlas";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(AtlasNotPresentException.class);
    }
}
