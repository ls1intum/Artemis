package de.tum.cit.aet.artemis.fileupload.architecture;

import de.tum.cit.aet.artemis.fileupload.AbstractFileUploadIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

public class FileUploadTestArchitectureTest extends AbstractModuleTestArchitectureTest<AbstractFileUploadIntegrationTest> {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".fileupload";
    }

    @Override
    protected Class<AbstractFileUploadIntegrationTest> getAbstractModuleIntegrationTestClass() {
        return AbstractFileUploadIntegrationTest.class;
    }
}
