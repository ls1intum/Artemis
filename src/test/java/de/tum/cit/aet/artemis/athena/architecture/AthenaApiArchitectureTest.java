package de.tum.cit.aet.artemis.athena.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.athena.config.AthenaApiNotPresentException;
import de.tum.cit.aet.artemis.athena.config.AthenaAuthorizationInterceptor;
import de.tum.cit.aet.artemis.athena.config.AthenaEnabled;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class AthenaApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".athena";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        // @formatter:off
        return Set.of(
            AthenaAuthorizationInterceptor.class,
            AthenaEnabled.class,
            AthenaApiNotPresentException.class
        );
        // @formatter:on
    }
}
