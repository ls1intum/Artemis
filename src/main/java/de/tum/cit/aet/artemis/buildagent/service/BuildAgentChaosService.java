package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CHAOS;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Profile(PROFILE_BUILDAGENT + " & " + PROFILE_CHAOS)
public class BuildAgentChaosService {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentChaosService.class);

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    private final RedissonClient redissonClient;

    public BuildAgentChaosService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Initialize listener on kill build agent messages
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        RTopic killBuildAgentTopic = redissonClient.getTopic("killBuildAgent");
        killBuildAgentTopic.addListener(String.class, (channel, name) -> {
            log.info("Received message to kill build agent {}", name);
            if (buildAgentShortName.equals(name)) {
                killBuildAgent();
            }
        });
    }

    /**
     * Kill the build agent. Simulating an unexpected crash failure
     */
    private void killBuildAgent() {
        log.info("Killing build agent");
        System.exit(-1);
    }

}
