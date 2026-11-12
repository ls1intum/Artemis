package de.tum.cit.aet.artemis.metis.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleServiceArchitectureTest;

/**
 * Applies the service rules of every module to Metis: each {@code @Service} is named {@code *Service}, lives in the service package and is neither final nor a
 * {@code @Component}. The scheduler of the generation is a {@code @Component} on purpose and therefore not named {@code *Service}.
 */
class MetisServiceArchitectureTest extends AbstractModuleServiceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".metis";
    }
}
