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

    // TODO: The localci REST resources still expose their endpoints under "api/programming/..." for
    // backwards compatibility with existing clients. Once the URLs are migrated to "api/localci/..."
    // these exemptions should be removed.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(BuildJobQueueResource.class, BuildLogResource.class, BuildPhasesTemplateResource.class, BuildPlanResource.class, PublicBuildPlanResource.class);
    }
}
