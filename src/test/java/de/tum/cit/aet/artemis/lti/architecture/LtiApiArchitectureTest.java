package de.tum.cit.aet.artemis.lti.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.SecurityConfiguration;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class LtiApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(Constants.class, SecurityConfiguration.class);
    }
}
