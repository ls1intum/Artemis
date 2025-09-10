package de.tum.cit.aet.artemis.lti.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;
import de.tum.cit.aet.artemis.lti.web.LtiResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class LtiApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(LtiResource.class, CustomLti13Configurer.class);
    }
}
