package de.tum.cit.aet.artemis.modeling.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class ModelingApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".modeling";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(ModelingApiNotPresentException.class);
    }
}
