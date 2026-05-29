package de.tum.cit.aet.artemis.admin.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.admin.web.AdminAuditResource;
import de.tum.cit.aet.artemis.admin.web.AdminBuildJobQueueResource;
import de.tum.cit.aet.artemis.admin.web.AdminCleanupResource;
import de.tum.cit.aet.artemis.admin.web.AdminCourseRequestResource;
import de.tum.cit.aet.artemis.admin.web.AdminCourseResource;
import de.tum.cit.aet.artemis.admin.web.AdminDataExportResource;
import de.tum.cit.aet.artemis.admin.web.AdminFeatureToggleResource;
import de.tum.cit.aet.artemis.admin.web.AdminImprintResource;
import de.tum.cit.aet.artemis.admin.web.AdminLogResource;
import de.tum.cit.aet.artemis.admin.web.AdminMetricsResource;
import de.tum.cit.aet.artemis.admin.web.AdminOrganizationResource;
import de.tum.cit.aet.artemis.admin.web.AdminPrivacyStatementResource;
import de.tum.cit.aet.artemis.admin.web.AdminSbomResource;
import de.tum.cit.aet.artemis.admin.web.AdminScheduleResource;
import de.tum.cit.aet.artemis.admin.web.AdminStatisticsResource;
import de.tum.cit.aet.artemis.admin.web.AdminWebsocketResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class AdminResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".admin";
    }

    // TODO: The admin REST resources expose their endpoints under both "api/admin/..." (preferred)
    // and the legacy "api/core/admin/..." prefix kept for backwards compatibility with existing
    // clients. Once the legacy prefix is removed these exemptions should be removed.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(AdminAuditResource.class, AdminBuildJobQueueResource.class, AdminCleanupResource.class, AdminCourseRequestResource.class, AdminCourseResource.class,
                AdminDataExportResource.class, AdminFeatureToggleResource.class, AdminImprintResource.class, AdminLogResource.class, AdminMetricsResource.class,
                AdminOrganizationResource.class, AdminPrivacyStatementResource.class, AdminSbomResource.class, AdminScheduleResource.class, AdminStatisticsResource.class,
                AdminWebsocketResource.class);
    }

}
