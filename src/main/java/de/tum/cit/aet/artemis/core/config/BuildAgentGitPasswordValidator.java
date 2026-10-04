package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME;
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
 * Refuses to start a production node whose build-agent git password is a value Artemis ships as an example.
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
     * Rejects a shipped example or blank build-agent git password under the production profile, unless the build agents
     * authenticate with an ssh key, in which case the value no longer opens the shortcut this check protects.
     * <p>
     * Matching credentials let a caller read every repository in the installation: {@code LocalVCServletService}
     * returns early on a match, ahead of the rate limit, the repository authorization checks and the VCS access log.
     * That shortcut is the only thing the property closes; the credentials are still processed as ordinary Basic
     * credentials afterwards, which grants only whatever the named account may access.
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

        if (environment.getProperty(BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)) {
            // The build agents authenticate with an ssh key, so LocalVCServletService no longer lets this credential
            // pair read every repository, which is the shortcut this check protects. The pair still reaches ordinary
            // Basic authentication afterwards, but there it opens only what the named account may access, and the
            // shipped example username is not an Artemis account. Refusing to start over a password that can no longer
            // grant repository-wide read would only push operators to invent one. The check re-arms by itself, because
            // it runs on every startup and therefore also on the one that follows setting the property back to false.
            log.info("Skipping build-agent git password validation: {} is true, so the build-agent git credentials no longer grant read access to every repository",
                    BUILD_AGENT_USE_SSH_PROPERTY_NAME);
            return;
        }

        String buildAgentGitPassword = environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY);
        if (buildAgentGitPassword == null) {
            // Only the localvc and buildagent profiles define the property at all, so an instance running neither has
            // no build-agent shortcut to protect.
            return;
        }
        if (!StringUtils.hasText(buildAgentGitPassword)) {
            // Blank is not a security problem, and is now the state worth aiming for. LocalVCServletService requires
            // hasText on *both* credentials before it compares them, so a blank pair can never match: there is no
            // shortcut for an empty password to open. What blank does mean is that the pair cannot authenticate
            // anything, which is exactly right on a node whose build agents use per-build-job clone tokens or ssh keys,
            // and which only matters for a client that has neither - Jenkins with LocalVC.
            //
            // This used to fail startup, on the reasoning that an empty configured password would be matched by an
            // empty supplied one. The hasText guard in the servlet makes that unreachable, and failing here refused the
            // configuration with no shared secret anywhere - the opposite of what this validator exists to encourage.
            log.info("The build-agent git password is blank, so the shared credential pair is not accepted at all. That is expected where build agents authenticate with a "
                    + "per-build-job clone token or an ssh key. Configure both credentials only if a client that is not an Artemis build agent, such as Jenkins with LocalVC, "
                    + "clones from this installation.");
            return;
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
