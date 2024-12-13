package de.tum.cit.aet.artemis.programming.web.localvc.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshFingerprintsProviderService;

/**
 * REST controller for managing.
 */
@Profile(PROFILE_LOCALVC)
@RestController
@RequestMapping("api/")
public class SshFingerprintsProviderResource {

    SshFingerprintsProviderService sshFingerprintsProviderService;

    public SshFingerprintsProviderResource(SshFingerprintsProviderService sshFingerprintsProviderService) {
        this.sshFingerprintsProviderService = sshFingerprintsProviderService;
    }

    /**
     * GET /ssh-fingerprints
     *
     * @return the SSH fingerprints for the keys a user uses
     */
    @GetMapping(value = "ssh-fingerprints", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, String>> getSshFingerprints() {
        return ResponseEntity.ok().body(sshFingerprintsProviderService.getSshFingerPrints());
    }
}
