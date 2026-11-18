package de.tum.cit.aet.artemis.atlas.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

class AtlasTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".atlas";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractAtlasIntegrationTest.class);
    }
}
