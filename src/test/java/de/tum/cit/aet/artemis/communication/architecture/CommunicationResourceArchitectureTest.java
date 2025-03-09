package de.tum.cit.aet.artemis.communication.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.communication.web.AndroidAppSiteAssociationResource;
import de.tum.cit.aet.artemis.communication.web.AppleAppSiteAssociationResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class CommunicationResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".communication";
    }

    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(AndroidAppSiteAssociationResource.class, AppleAppSiteAssociationResource.class);
    }
}
