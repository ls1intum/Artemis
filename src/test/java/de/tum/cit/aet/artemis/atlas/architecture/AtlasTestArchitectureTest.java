package de.tum.cit.aet.artemis.atlas.architecture;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

class AtlasTestArchitectureTest extends AbstractModuleTestArchitectureTest<AbstractAtlasIntegrationTest> {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".atlas";
    }

    @Override
    protected Class<AbstractAtlasIntegrationTest> getAbstractModuleIntegrationTestClass() {
        return AbstractAtlasIntegrationTest.class;
    }
}
