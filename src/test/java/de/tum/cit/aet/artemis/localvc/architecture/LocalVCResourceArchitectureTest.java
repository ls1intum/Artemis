package de.tum.cit.aet.artemis.localvc.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.localvc.web.ssh.SshFingerprintsProviderResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class LocalVCResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localvc";
    }

    // The localvc REST resource now exposes its endpoints under the canonical "api/localvc/..." prefix,
    // but its @RequestMapping still carries the legacy "api/programming/..." alias for backwards
    // compatibility with existing clients. The prefix rule checks every declared path value, so the legacy
    // alias trips it — hence this exemption remains necessary.
    // TODO: remove this exemption once the legacy "api/programming/..." alias is dropped at the sunset
    // date (2026-09-30, see LegacyApiPathDeprecationInterceptor#SUNSET_DATE).
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(SshFingerprintsProviderResource.class);
    }
}
