package de.tum.cit.aet.artemis.lecture.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.service.SlideUnhideService;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class LectureApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lecture";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        // TODO: remove SlideUnhideService once activated again
        return Set.of(LectureApiNotPresentException.class, SlideUnhideService.class);
    }
}
