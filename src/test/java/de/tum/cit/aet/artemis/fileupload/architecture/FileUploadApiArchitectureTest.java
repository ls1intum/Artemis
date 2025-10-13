package de.tum.cit.aet.artemis.fileupload.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class FileUploadApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".fileupload";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(FileUploadExerciseRepository.class);
    }
}
