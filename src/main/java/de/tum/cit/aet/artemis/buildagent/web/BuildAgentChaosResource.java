package de.tum.cit.aet.artemis.buildagent.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;

/**
 * REST controller for causing chaos on the build agent.
 */
@Profile(PROFILE_BUILDAGENT)
@EnforceAdmin
@RestController
@RequestMapping("api/buildagent/admin/chaos")
public class BuildAgentChaosResource {

    /**
     * Kill the build agent. Trying simple System.exit() first.
     */
    @PostMapping("/kill")
    public void kill() {
        System.exit(-1);
    }
}
