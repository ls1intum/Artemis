package de.tum.cit.aet.artemis.admin.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.admin.web.AdminCourseResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class AdminResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".admin";
    }

    // TODO: AdminCourseResource is the last admin resource that still serves the legacy
    // "api/core/admin/..." prefix alongside the canonical "api/admin/...", because artemis-android
    // creates courses through POST api/core/admin/courses. Remove this exemption together with the
    // alias once artemis-android#694 has shipped.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(AdminCourseResource.class);
    }
}
