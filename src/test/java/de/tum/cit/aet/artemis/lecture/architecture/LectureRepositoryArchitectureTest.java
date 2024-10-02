package de.tum.cit.aet.artemis.lecture.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

public class LectureRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lecture";
    }

    // TODO: This method should be removed once all repositories are tested
    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of(
                "de.tum.cit.aet.artemis.lecture.service.LectureImportService.importLecture(de.tum.cit.aet.artemis.lecture.domain.Lecture, de.tum.cit.aet.artemis.core.domain.Course)");
    }
}
