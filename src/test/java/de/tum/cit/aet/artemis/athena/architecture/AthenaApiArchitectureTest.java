package de.tum.cit.aet.artemis.athena.architecture;

import java.util.Set;

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
        // AthenaAuthorizationInterceptor and AthenaEnabled are intentionally referenced from core (RestTemplateConfiguration)
        // so the @Conditional/@Profile and the interceptor wiring can live alongside the other RestTemplates.
        return Set.of(AthenaAuthorizationInterceptor.class, AthenaEnabled.class);
    }
}
