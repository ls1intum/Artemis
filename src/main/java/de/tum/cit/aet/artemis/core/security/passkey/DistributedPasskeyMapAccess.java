package de.tum.cit.aet.artemis.core.security.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.dto.passkey.PublicKeyCredentialCreationOptionsDTO;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;

/**
 * Access wrapper for passkey-related distributed maps.
 * Repositories should depend on this component instead of service beans.
 */
@Lazy
@Profile(PROFILE_CORE)
@Component
public class DistributedPasskeyMapAccess {

    private final DistributedDataAccessService distributedDataAccessService;

    public DistributedPasskeyMapAccess(DistributedDataAccessService distributedDataAccessService) {
        this.distributedDataAccessService = distributedDataAccessService;
    }

    public DistributedMap<String, PublicKeyCredentialCreationOptionsDTO> getDistributedPasskeyCreationOptionsMap() {
        return distributedDataAccessService.getDistributedPasskeyCreationOptionsMap();
    }

    public Map<String, PublicKeyCredentialCreationOptionsDTO> getPasskeyCreationOptionsMap() {
        return distributedDataAccessService.getPasskeyCreationOptionsMap();
    }

    public DistributedMap<String, PublicKeyCredentialRequestOptions> getDistributedPasskeyAuthOptionsMap() {
        return distributedDataAccessService.getDistributedPasskeyAuthOptionsMap();
    }

    public Map<String, PublicKeyCredentialRequestOptions> getPasskeyAuthOptionsMap() {
        return distributedDataAccessService.getPasskeyAuthOptionsMap();
    }
}
