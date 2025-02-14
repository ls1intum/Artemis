package de.tum.cit.aet.artemis.core.config.conditions;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Helper class for property configuration, in particular for determining conditions
 * whether services should be enabled or not.
 * This bridges the gap between the condition classes and the actual property values.
 */
public class ArtemisConfigHelper {

    public static boolean isBuildAgentOnlyMode(Environment environment) {
        String artemisMode = environment.getProperty("artemis.mode", String.class);
        return "buildagent-only".equals(artemisMode);
    }

    public static boolean isBuildAgentEnabled(Environment environment) {
        String artemisMode = environment.getProperty("artemis.mode", String.class);
        if (isJenkinsEnabled(environment) || isGitLabCIEnabled(environment)) {
            return false;
        }

        return "default".equals(artemisMode) || "buildagent-only".equals(artemisMode);
    }

    public static boolean isJenkinsEnabled(Environment environment) {
        return environment.acceptsProfiles(Profiles.of(PROFILE_JENKINS));
    }

    public static boolean isGitLabEnabled(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("gitlab"));
    }

    public static boolean isGitLabCIEnabled(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("gitlabci"));
    }

    public static boolean isAthenaEnabled(Environment environment) {
        return environment.getProperty("artemis.athena.enabled", Boolean.class, false);
    }

    // This provides room for future extension
}
