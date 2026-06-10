package de.tum.cit.aet.artemis.localci.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.localci.web.BuildJobQueueResource;
import de.tum.cit.aet.artemis.localci.web.BuildLogResource;
import de.tum.cit.aet.artemis.localci.web.BuildPhasesTemplateResource;
import de.tum.cit.aet.artemis.localci.web.BuildPlanResource;
import de.tum.cit.aet.artemis.localci.web.open.PublicBuildPlanResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class LocalCIResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localci";
    }

    // The localci REST resources now expose their endpoints under the canonical "api/localci/..." prefix,
    // but each @RequestMapping still carries the legacy "api/programming/..." alias for backwards
    // compatibility with existing clients. The prefix rule checks every declared path value, so the legacy
    // alias trips it — hence these exemptions remain necessary.
    // TODO: remove these exemptions once the legacy "api/programming/..." aliases are dropped at the sunset
    // date (2026-09-30, see LegacyApiPathDeprecationInterceptor#SUNSET_DATE).
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(BuildJobQueueResource.class, BuildLogResource.class, BuildPhasesTemplateResource.class, BuildPlanResource.class, PublicBuildPlanResource.class);
    }
}
