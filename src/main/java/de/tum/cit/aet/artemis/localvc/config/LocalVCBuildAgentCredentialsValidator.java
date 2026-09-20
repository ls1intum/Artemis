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
     * Rejects a node whose build agent authentication is either impossible or a shared secret it no longer needs, and
     * otherwise records which mechanism it accepts.
     * <p>
     * The logged line matters because a core node configured for ssh rejects an agent that still clones over https, and
     * in a multi node setup the two settings live in separate files. That failure surfaces as an authentication error
     * saying nothing about the cause, so having each node state what it accepts makes the disagreement visible.
     *
     * @throws IllegalStateException if this node runs local CI and still configures the shared credential pair, or if
     *                                   it runs no local CI, has ssh disabled and the pair is not both configured,
     *                                   leaving nothing that could authenticate a client
     */
    @PostConstruct
    public void validateBuildAgentCredentials() {
        boolean anyCredentialConfigured = StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_USERNAME_PROPERTY))
                || StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY));
        boolean credentialPairConfigured = StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_USERNAME_PROPERTY))
                && StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY));
        boolean localCiActive = environment.matchesProfiles(PROFILE_LOCALCI);

        // Checked before the ssh branch below, because the objection is to the credential existing at all rather than
        // to it being reachable. With ssh the pair is not offered to build agents, but it stays a valid Basic
        // credential on this node, and leaving one configured is what makes it a shared secret worth stealing.
        if (localCiActive && anyCredentialConfigured) {
            throw new IllegalStateException("This node runs local CI and must not configure a shared build-agent git credential. Clear " + BUILD_AGENT_GIT_USERNAME_PROPERTY
                    + " and " + BUILD_AGENT_GIT_PASSWORD_PROPERTY
                    + ". Build agents authenticate per build job here: over https with the job's clone token, which covers its assignment, test, solution and auxiliary "
                    + "repositories and stops working when the job ends, or with an ssh key by setting " + BUILD_AGENT_USE_SSH_PROPERTY_NAME
                    + " to true, which is preferred. One static secret held by every agent and every core node opens every repository in the installation, so it is refused "
                    + "here rather than deprecated. It remains available on a node without local CI, which is the Jenkins with LocalVC setup.");
        }

        if (environment.getProperty(BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)) {
            log.info("Build agents authenticate with an ssh key ({}=true), scoped to the repositories of the build jobs they are running.", BUILD_AGENT_USE_SSH_PROPERTY_NAME);
            return;
        }

        if (localCiActive) {
            log.info("Build agents authenticate with the per-build-job clone token, which covers that job's assignment, test, solution and auxiliary repositories and nothing "
                    + "else. No shared build-agent git credential is configured, and configuring one is refused on a local CI node.");
            return;
        }

        if (!credentialPairConfigured) {
            throw new IllegalStateException("This node cannot authenticate any build agent: it does not run local CI, so there are no build jobs and no clone tokens, "
                    + BUILD_AGENT_USE_SSH_PROPERTY_NAME + " is false, and " + BUILD_AGENT_GIT_USERNAME_PROPERTY + " and " + BUILD_AGENT_GIT_PASSWORD_PROPERTY
                    + " are not both configured. Set both to the values the build agents use, or set the first property to true on the build agents and on every core node to "
                    + "authenticate with an ssh key instead.");
        }

        log.info("Build agents authenticate with the configured git username and password, which is deprecated and only supported because this node runs no local CI. Set {}=true "
                + "on the build agents and on every core node to use ssh keys instead.", BUILD_AGENT_USE_SSH_PROPERTY_NAME);
    }
}
