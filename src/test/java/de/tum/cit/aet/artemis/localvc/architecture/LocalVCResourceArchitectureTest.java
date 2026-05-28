package de.tum.cit.aet.artemis.localvc.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.localvc.web.ssh.SshFingerprintsProviderResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class LocalVCResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localvc";
    }

    // TODO: The localvc REST resource still exposes its endpoints under "api/programming/..." for
    // backwards compatibility with existing clients. Once the URLs are migrated to "api/localvc/..."
    // these exemptions should be removed.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(SshFingerprintsProviderResource.class);
    }
}
