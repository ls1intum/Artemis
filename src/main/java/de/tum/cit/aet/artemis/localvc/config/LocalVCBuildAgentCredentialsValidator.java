package de.tum.cit.aet.artemis.localvc.config;

import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Refuses to start a local VC node that can accept none of the mechanisms build agents use to clone repositories, and
 * logs which ones it does accept.
 * <p>
 * There are three. An Artemis build agent authenticates either with the key pair it generates at startup, selected by
 * {@code artemis.version-control.build-agent-use-ssh}, or over https with the clone token of the build job it is
 * running. Both are per agent or per job and need no configuration. The third is the shared build-agent git username
 * and password, which is deprecated and now only needed by a client that is not an Artemis build agent and therefore
 * has neither a key nor a build job - in practice Jenkins in the Jenkins with LocalVC setup.
 * <p>
 * That is why a blank credential pair is no longer an error on a local CI node: clone tokens always work there, so
 * refusing to start would reject exactly the configuration worth aiming for, one with no shared secret at all. On a
 * node without local CI there are no build jobs and hence no tokens, so the pair remains the only mechanism and its
 * absence is still fatal.
 * <p>
 * This runs eagerly, unlike {@code LocalVCServletService}, which is lazy and therefore first initialised by a git
 * request that has already reached the node. Failing at startup keeps the error next to the setting that caused it
 * rather than surfacing it as an authentication failure in a build log much later.
 *
 * @see de.tum.cit.aet.artemis.core.config.BuildAgentGitPasswordValidator
 */
@Component
@Profile(PROFILE_LOCALVC)
@Lazy(false)
public class LocalVCBuildAgentCredentialsValidator {

    private static final Logger log = LoggerFactory.getLogger(LocalVCBuildAgentCredentialsValidator.class);

    private static final String BUILD_AGENT_GIT_USERNAME_PROPERTY = "artemis.version-control.build-agent-git-username";

    private static final String BUILD_AGENT_GIT_PASSWORD_PROPERTY = "artemis.version-control.build-agent-git-password";

    private final Environment environment;

    public LocalVCBuildAgentCredentialsValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Rejects a node that has no way to authenticate build agents, and otherwise records which mechanisms it accepts.
     * <p>
     * The logged line matters because a core node configured for ssh rejects an agent that still clones over https, and
     * in a multi node setup the two settings live in separate files. That failure surfaces as an authentication error
     * saying nothing about the cause, so having each node state what it accepts makes the disagreement visible.
     *
     * @throws IllegalStateException if this node has no local CI, ssh is disabled and the build-agent git credentials
     *                                   are not both configured, leaving nothing that could authenticate a client
     */
    @PostConstruct
    public void validateBuildAgentCredentials() {
        boolean credentialPairConfigured = StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_USERNAME_PROPERTY))
                && StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY));

        if (environment.getProperty(BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)) {
            log.info("Build agents authenticate with an ssh key ({}=true), scoped to the repositories of the build jobs they are running. This node no longer grants the "
                    + "build-agent git username and password read access to every repository.", BUILD_AGENT_USE_SSH_PROPERTY_NAME);
            return;
        }

        if (environment.matchesProfiles(PROFILE_LOCALCI)) {
            // Local CI issues a clone token per build job, so build agents can always authenticate here regardless of
            // the credential pair. A blank pair is the desirable state and must not stop the node from starting.
            if (credentialPairConfigured) {
                log.info(
                        "Build agents authenticate with the per-build-job clone token. The configured {} and {} are also still accepted, which is deprecated: clear both unless "
                                + "a client that is not an Artemis build agent, such as Jenkins, clones from this node.",
                        BUILD_AGENT_GIT_USERNAME_PROPERTY, BUILD_AGENT_GIT_PASSWORD_PROPERTY);
            }
            else {
                log.info("Build agents authenticate with the per-build-job clone token, and no shared build-agent git credentials are configured.");
            }
            return;
        }

        if (!credentialPairConfigured) {
            throw new IllegalStateException("This node cannot authenticate any build agent: it does not run local CI, so there are no build jobs and no clone tokens, "
                    + BUILD_AGENT_USE_SSH_PROPERTY_NAME + " is false, and " + BUILD_AGENT_GIT_USERNAME_PROPERTY + " and " + BUILD_AGENT_GIT_PASSWORD_PROPERTY
                    + " are not both configured. Set both to the values the build agents use, or set the first property to true on the build agents and on every core node to "
                    + "authenticate with an ssh key instead.");
        }

        log.info("Build agents authenticate with the configured git username and password, which is deprecated. Set {}=true on the build agents and on every core node to use "
                + "ssh keys instead.", BUILD_AGENT_USE_SSH_PROPERTY_NAME);
    }
}
