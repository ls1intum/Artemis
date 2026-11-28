package de.tum.cit.aet.artemis.metis.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

/**
 * Applies the repository rules of every module to Metis. Besides the JPQL style, the {@code @Param} annotations and the transaction boundaries, these rules
 * require every method of the four Metis repositories to have a caller in production code, so a query that only a test uses fails here.
 */
class MetisRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".metis";
    }
}
