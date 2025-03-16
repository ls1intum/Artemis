package de.tum.cit.aet.artemis.chaos.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CHAOS;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.chaos.service.ChaosService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;

/**
 * REST controller for causing chaos on the build agent.
 */
@Profile({ PROFILE_CHAOS, PROFILE_LOCALCI })
@EnforceAdmin
@RestController
@RequestMapping("api/chaos/")
public class BuildAgentChaosResource {

    private final ChaosService chaosService;

    public BuildAgentChaosResource(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    /**
     * Kill random build agent.
     */
    @PostMapping("kill-build-agent/{agentName}")
    public ResponseEntity<Void> kill(@PathVariable String agentName) {
        chaosService.triggerKillBuildAgent(agentName);
        return ResponseEntity.noContent().build();
    }
}
