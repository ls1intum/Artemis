package de.tum.cit.aet.artemis.course.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.course.web.CourseAccessResource;
import de.tum.cit.aet.artemis.course.web.CourseArchiveResource;
import de.tum.cit.aet.artemis.course.web.CourseManagementResource;
import de.tum.cit.aet.artemis.course.web.CourseMaterialImportResource;
import de.tum.cit.aet.artemis.course.web.CourseOverviewResource;
import de.tum.cit.aet.artemis.course.web.CourseRequestResource;
import de.tum.cit.aet.artemis.course.web.CourseStatsResource;
import de.tum.cit.aet.artemis.course.web.CourseUpdateResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class CourseResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".course";
    }

    // TODO: The course REST resources still expose their endpoints under "api/core/..." for
    // backwards compatibility with existing clients. Once the URLs are migrated to "api/course/..."
    // these exemptions should be removed.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(CourseAccessResource.class, CourseArchiveResource.class, CourseManagementResource.class, CourseMaterialImportResource.class, CourseOverviewResource.class,
                CourseRequestResource.class, CourseStatsResource.class, CourseUpdateResource.class);
    }
}
