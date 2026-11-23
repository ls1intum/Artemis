package de.tum.cit.aet.artemis.modeling.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.modeling.config.ApollonEnabled;
import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class ModelingApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".modeling";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        // ApollonEnabled is intentionally referenced from core (RestClientConfiguration) so the Apollon RestClient
        // beans can be conditionally created next to the other RestClients.
        return Set.of(ModelingApiNotPresentException.class, ApollonEnabled.class);
    }
}
