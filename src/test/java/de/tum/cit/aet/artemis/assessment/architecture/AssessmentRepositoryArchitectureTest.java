package de.tum.cit.aet.artemis.assessment.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class AssessmentRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".assessment";
    }

    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of(
                // Test utility method that uses EntityManager to bulk-update lastModifiedDate for deterministic test results
                "de.tum.cit.aet.artemis.assessment.util.StudentScoreUtilService.normalizeLastModifiedDates(de.tum.cit.aet.artemis.core.domain.User, java.time.Instant)");
    }
}
