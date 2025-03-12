package de.tum.cit.aet.artemis.athena.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.athena.config.AthenaAuthorizationInterceptor;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class AthenaApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".athena";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(AthenaAuthorizationInterceptor.class);
    }
}
