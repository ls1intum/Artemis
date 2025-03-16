package de.tum.cit.aet.artemis.chaos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CHAOS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import jakarta.annotation.PostConstruct;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({ PROFILE_CHAOS, PROFILE_LOCALCI })
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    private final RedissonClient redissonClient;

    private RTopic killBuildAgentTopic;

    public ChaosService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void init() {
        killBuildAgentTopic = redissonClient.getTopic("killBuildAgent");
    }

    public void triggerKillBuildAgent(String agentName) {
        log.info("Request to Kill build agent {}", agentName);
        killBuildAgentTopic.publish(agentName);
    }
}
