package de.tum.cit.aet.artemis.plagiarism.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Plagiarism module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 */
class PlagiarismEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".plagiarism";
    }

    @Override
    protected int getExpectedEntityReturnViolations() {
        return 0;
    }

    // Plagiarism request bodies are fully DTO-based; do not let any new entity @RequestBody slip back in.
    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }

    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 0;
    }
}
