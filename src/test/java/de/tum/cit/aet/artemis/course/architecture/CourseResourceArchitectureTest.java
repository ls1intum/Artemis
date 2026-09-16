package de.tum.cit.aet.artemis.course.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.course.web.CourseAccessResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class CourseResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".course";
    }

    // TODO: CourseAccessResource is the last course resource that still serves the legacy "api/core/..."
    // prefix alongside the canonical "api/course/...", because the released artemis-android calls
    // api/core/courses/for-enrollment, api/core/courses/{courseId}/enroll and
    // api/core/courses/{courseId}/users/search. Remove this exemption together with the alias once
    // artemis-android#694 has shipped and the sunset has passed.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(CourseAccessResource.class);
    }
}
