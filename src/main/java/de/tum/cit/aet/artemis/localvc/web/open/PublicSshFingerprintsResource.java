package de.tum.cit.aet.artemis.localvc.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshFingerprintsProviderService;

/**
 * Publishes the fingerprints of the SSH host keys of this node without a login.
 * <p>
 * A build agent that clones over SSH compares the host key it is shown with these fingerprints. It has no user account to sign in with, so the
 * authenticated endpoint {@code api/localvc/ssh-fingerprints} is not an option for it. The fingerprints are the same values that every user sees on the page
 * for SSH keys, and they are what {@code ssh-keyscan} reveals to anyone who connects to the SSH port, so publishing them adds nothing.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@RestController
@RequestMapping("api/localvc/public/")
@FeatureUsage(UserFeature.BUILD_AGENTS)
public class PublicSshFingerprintsResource {

    private final SshFingerprintsProviderService sshFingerprintsProviderService;

    public PublicSshFingerprintsResource(SshFingerprintsProviderService sshFingerprintsProviderService) {
        this.sshFingerprintsProviderService = sshFingerprintsProviderService;
    }

    /**
     * GET ssh-fingerprints : the SHA-256 fingerprints of the SSH host keys of this node.
     *
     * @return the fingerprints by the algorithm of the host key
     */
    @UsageInteraction(FeatureInteraction.SYSTEM)
    @GetMapping(value = "ssh-fingerprints", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceNothing
    public ResponseEntity<Map<String, String>> getSshFingerprints() {
        return ResponseEntity.ok(sshFingerprintsProviderService.getSshFingerPrints());
    }
}
