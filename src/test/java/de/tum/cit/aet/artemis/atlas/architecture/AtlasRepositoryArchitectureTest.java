package de.tum.cit.aet.artemis.atlas.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class AtlasRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".atlas";
    }

    @Override
    protected Set<String> enforceStructureOfTestRepositoriesExclusions() {
        return Set.of("de.tum.cit.aet.artemis.atlas.test_repository.LearningPathTestRepository");
    }
}
