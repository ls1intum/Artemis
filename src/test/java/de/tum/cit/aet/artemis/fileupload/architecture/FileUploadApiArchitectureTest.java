package de.tum.cit.aet.artemis.fileupload.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class FileUploadApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".fileupload";
    }
}
