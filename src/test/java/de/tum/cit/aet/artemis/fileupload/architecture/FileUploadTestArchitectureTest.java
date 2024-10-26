package de.tum.cit.aet.artemis.fileupload.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.fileupload.AbstractFileUploadIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

class FileUploadTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".fileupload";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractFileUploadIntegrationTest.class);
    }
}
