package de.tum.cit.aet.artemis.exam.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class ExamApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exam";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(ExamApiNotPresentException.class);
    }
}
