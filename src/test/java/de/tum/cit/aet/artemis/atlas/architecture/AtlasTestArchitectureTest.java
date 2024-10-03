package de.tum.cit.aet.artemis.atlas.architecture;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.AbstractModuleTestArchitectureTest;

class AtlasTestArchitectureTest extends AbstractModuleTestArchitectureTest<AbstractAtlasIntegrationTest> {

    @Override
    protected String getModulePackageName() {
        return "atlas";
    }

    @Override
    protected Class<AbstractAtlasIntegrationTest> getAbstractModuleIntegrationTestClass() {
        return AbstractAtlasIntegrationTest.class;
    }
}
