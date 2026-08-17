package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.exception.InsecureDefaultCredentialException;

/**
 * Refuses to start a production node whose build-agent git password is a value Artemis ships as an example, or is blank.
 * <p>
 * This lives outside {@link ConfigurationValidator} because of which nodes have to run it. That validator is
 * {@code @Profile(PROFILE_CORE)}, while a build agent is a supported topology on its own: the multi-node setup runs a
 * node with the {@code buildagent} profile and nothing else, and {@code config/application-buildagent.yml} ships
 * {@code buildjob_password}. Housing the check in the core-only validator therefore skipped exactly the nodes that
 * carry the shipped default, and the mismatch only surfaced later, when an https clone reached a core node configured
 * with a different credential.
 * <p>
 * Active for either profile, so a node that is both core and build agent still runs it exactly once, and a
 * core-only node keeps the check it had. The core-only JWT and internal-admin checks stay where they are, because a
 * build agent configures neither.
 *
 * @see ConfigurationValidator
 */
@Component
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Lazy(false)
public class BuildAgentGitPasswordValidator {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentGitPasswordValidator.class);

    static final String BUILD_AGENT_GIT_PASSWORD_PROPERTY = "artemis.version-control.build-agent-git-password";

    /**
     * Build-agent git passwords that Artemis has shipped as examples: {@code buildjob_password} from
     * {@code config/application-localvc.yml} and {@code config/application-buildagent.yml},
     * {@code buildagent_password} from the production-setup security documentation, and {@code artemis_admin} from the
     * Jenkins LocalVC setup, which reuses the internal-admin password here.
     * <p>
     * Entries must never be removed: a value that was once published stays published.
     */
    private static final Set<String> KNOWN_DEFAULT_BUILD_AGENT_GIT_PASSWORDS = Set.of("buildjob_password", "buildagent_password", "artemis_admin");

    private final Environment environment;

    public BuildAgentGitPasswordValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Rejects a shipped example or blank build-agent git password under the production profile.
     * <p>
     * Matching credentials let a caller read every repository in the installation: {@code LocalVCServletService}
     * returns early on a match, ahead of the rate limit, the repository authorization checks and the VCS access log.
     * <p>
     * This throws rather than warning, because a warning in a startup log is routinely missed and the whole point is
     * that the unsafe state must not reach a running production system.
     */
    @PostConstruct
    public void validateBuildAgentGitPassword() {
        if (!environment.matchesProfiles(ArtemisConstants.SPRING_PROFILE_PRODUCTION)) {
            // Local development, tests and CI keep working with the packaged defaults.
            return;
        }

        String buildAgentGitPassword = environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY);
        if (buildAgentGitPassword == null) {
            // Only the localvc and buildagent profiles define the property at all, so an instance running neither has
            // no build-agent shortcut to protect.
            return;
        }
        if (!StringUtils.hasText(buildAgentGitPassword)) {
            // A configured but blank value is worse than a shipped default: LocalVCServletService compares the supplied
            // Basic credentials against it directly, so the published build-agent username with an empty password would
            // pass, again ahead of the rate limit, the authorization checks and the access log.
            throw new InsecureDefaultCredentialException(BUILD_AGENT_GIT_PASSWORD_PROPERTY,
                    "the build-agent git password is configured but blank, and a caller presenting the build-agent username with an empty password can then read every "
                            + "repository without any authorization check or access-log entry",
                    "Set a unique, non-blank password and keep it in sync with the build agents' configuration. The property has to carry a value even when the agents "
                            + "authenticate with an ssh key, because the localvc and buildagent profiles require it to resolve.");
        }

        // Non-short-circuiting on purpose, so that every candidate is compared regardless of where the match sits.
        boolean matchesDefault = false;
        for (String knownDefault : KNOWN_DEFAULT_BUILD_AGENT_GIT_PASSWORDS) {
            matchesDefault |= constantTimeEquals(buildAgentGitPassword, knownDefault);
        }
        if (matchesDefault) {
            throw new InsecureDefaultCredentialException(BUILD_AGENT_GIT_PASSWORD_PROPERTY,
                    "the build-agent git password is a value published in the Artemis repository, and a caller presenting it can read every repository "
                            + "without any authorization check or access-log entry",
                    "Choose a unique password, and keep it in sync with the build agents' configuration.");
        }

        log.info("Production credential validation passed: the build-agent git password is not a shipped example value");
    }

    /**
     * Compares two secrets without leaking their relationship through timing. Startup is not a realistic
     * timing-attack surface, but comparing secrets this way costs nothing and keeps the intent explicit.
     *
     * @param first  the first value
     * @param second the second value
     * @return whether the two values are equal
     */
    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
}
