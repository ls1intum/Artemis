package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.Environment;

/** Startup checks shared by the universal and dedicated AI Worker executables. */
public final class AiWorkerProfileValidation {

    private AiWorkerProfileValidation() {
    }

    /**
     * Reject incompatible node profiles and providers before the worker connects to the cluster.
     *
     * @param environment resolved profiles and configuration of this process
     */
    public static void validate(Environment environment) {
        if (!environment.matchesProfiles(PROFILE_AIWORKER)) {
            return;
        }
        if (environment.matchesProfiles(PROFILE_CORE)) {
            throw new IllegalArgumentException("An AI Worker node cannot also run the core profile");
        }
        if (environment.getProperty("spring.main.web-application-type", WebApplicationType.class, WebApplicationType.NONE) != WebApplicationType.NONE) {
            throw new IllegalArgumentException("An AI Worker-only node cannot enable an HTTP server");
        }
        if (environment.matchesProfiles(PROFILE_BUILDAGENT + " | " + PROFILE_LOCALCI + " | " + PROFILE_LOCALVC)) {
            throw new IllegalArgumentException("An AI Worker-only node cannot use server or build-agent profiles");
        }
        if (DistributedDataProviderResolver.isProvider(environment, LOCAL)) {
            throw new IllegalArgumentException("An AI Worker-only node requires a shared Hazelcast or Redis data provider");
        }
        if (DistributedDataProviderResolver.isProvider(environment, HAZELCAST) && !environment.getProperty("eureka.client.enabled", Boolean.class, false)) {
            throw new IllegalArgumentException("An AI Worker-only node requires Eureka discovery with Hazelcast");
        }
    }
}
