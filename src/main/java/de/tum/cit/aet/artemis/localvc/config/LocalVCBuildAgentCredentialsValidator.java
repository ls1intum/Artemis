package de.tum.cit.aet.artemis.localvc.config;

import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME;
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
 * Refuses to start a local VC node that can accept neither of the two mechanisms build agents use to clone
 * repositories, and logs which one it does accept.
 * <p>
 * Build agents authenticate either with the key pair they generate at startup or with the build-agent git username and
 * password, selected by {@code artemis.version-control.build-agent-use-ssh}. A node with ssh disabled and no configured
 * credential pair accepts neither, so every build fails to clone. Both credentials are optional, because an ssh
 * installation must not have to configure a credential it never uses, which leaves this check as the thing that keeps
 * the https case from starting into a state where nothing can build.
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
     * Rejects a node that has no way to authenticate build agents, and otherwise records the mechanism it accepts.
     * <p>
     * The logged line matters because the property has to carry the same value on the build agents and on every core
     * node, and in a multi node setup those live in separate files. A mismatch fails every clone with an authentication
     * error that says nothing about the cause, so each side stating what it does makes the disagreement visible.
     *
     * @throws IllegalStateException if ssh is disabled and the build-agent git credentials are not both configured
     */
    @PostConstruct
    public void validateBuildAgentCredentials() {
        if (environment.getProperty(BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)) {
            log.info("Build agents authenticate with an ssh key ({}=true). This node rejects the build-agent git username and password.", BUILD_AGENT_USE_SSH_PROPERTY_NAME);
            return;
        }

        if (!StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_USERNAME_PROPERTY)) || !StringUtils.hasText(environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY))) {
            throw new IllegalStateException("This node cannot authenticate any build agent: " + BUILD_AGENT_USE_SSH_PROPERTY_NAME
                    + " is false, so build agents clone over https, but " + BUILD_AGENT_GIT_USERNAME_PROPERTY + " and " + BUILD_AGENT_GIT_PASSWORD_PROPERTY
                    + " are not both configured. Set both to the values the build agents use, or set the first property to true on the build agents and on every core node to "
                    + "authenticate with an ssh key instead.");
        }

        log.info("Build agents authenticate with the configured git username and password. Set {}=true on the build agents and on every core node to use ssh keys instead.",
                BUILD_AGENT_USE_SSH_PROPERTY_NAME);
    }
}
