package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

class BuildJobGitServiceTest extends AbstractArtemisBuildAgentTest {

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", false);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.of("somePath"));
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.of("someUrl"));
        // The service is a shared bean, so the credentials are restored here rather than in the tests that clear them
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "buildjob_user");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "buildjob_password");
        // The fallback provider is cached on the bean, so it would otherwise survive with a previous test's values
        ReflectionTestUtils.setField(buildJobGitService, "credentialsProvider", null);
        buildJobGitService.clearCloneTokenForCurrentThread();
    }

    @Test
    void shouldNotUseSshWhenUseSshBuildAgentDisabled() {
        assertThat(buildJobGitService.useSsh()).isFalse();
        buildJobGitService.init();
        // should not throw
    }

    @Test
    void shouldSucceedInitWhenUseSshBuildEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        assertThat(buildJobGitService.useSsh()).isTrue();
        buildJobGitService.init();
        // should not throw
    }

    @Test
    void shouldThrowWhenNoTemplateButUseBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.empty());
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> buildJobGitService.init());
    }

    @Test
    void shouldThrowWhenNoPrivateKeyButUseBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.empty());
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> buildJobGitService.init());
    }

    /**
     * A missing credential is no longer fatal over https, because a build job normally carries its own clone token and
     * an agent that has one needs no configured credential at all. Refusing to start would reject the configuration
     * worth aiming for, one with no shared secret anywhere. The pair only still matters for a job queued by a core node
     * that does not issue tokens yet, which is a transient state during a rolling upgrade.
     *
     * @param missingCredential the field left unset
     */
    @ParameterizedTest
    @ValueSource(strings = { "buildAgentGitUsername", "buildAgentGitPassword" })
    void shouldStartWithoutCredentialsAndUseSshBuildAgentDisabled(String missingCredential) {
        ReflectionTestUtils.setField(buildJobGitService, missingCredential, "");

        assertThatCode(() -> buildJobGitService.init()).doesNotThrowAnyException();
    }

    /**
     * The credentials are unused in the ssh case, so a missing one must not keep the agent from starting.
     */
    @Test
    void shouldSucceedInitWithoutCredentialsWhenUseSshBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "");

        buildJobGitService.init();
        // should not throw
    }

    /**
     * The credential a clone actually presents. With a build job's token bound to this thread the agent authenticates
     * as itself with that token, which opens only that job's repositories; without one it falls back to the deprecated
     * shared pair.
     */
    @Test
    void shouldPresentTheCloneTokenOfTheCurrentBuildJob() {
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentShortName", "artemis-build-agent-1");
        buildJobGitService.setCloneTokenForCurrentThread("bjct-the-token");
        try {
            var credentialsProvider = (UsernamePasswordCredentialsProvider) ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");

            CredentialItem.Username username = new CredentialItem.Username();
            CredentialItem.Password password = new CredentialItem.Password();
            credentialsProvider.get(new URIish(), username, password);

            assertThat(username.getValue()).as("the agent names itself so the core node knows whose job token to check").isEqualTo("artemis-build-agent-1");
            assertThat(password.getValue()).containsExactly("bjct-the-token".toCharArray());
        }
        finally {
            buildJobGitService.clearCloneTokenForCurrentThread();
        }
    }

    @Test
    void shouldFallBackToTheConfiguredCredentialsWithoutACloneToken() throws Exception {
        buildJobGitService.clearCloneTokenForCurrentThread();
        var credentialsProvider = (UsernamePasswordCredentialsProvider) ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");

        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();
        credentialsProvider.get(new URIish(), username, password);

        assertThat(username.getValue()).isEqualTo("buildjob_user");
        assertThat(password.getValue()).containsExactly("buildjob_password".toCharArray());
    }

    /**
     * Executor threads are reused between build jobs, so a token left behind would be presented for the next job,
     * whose repositories it does not cover and which would therefore fail to clone.
     */
    @Test
    void shouldNotLeakACloneTokenToTheNextBuildJob() {
        buildJobGitService.setCloneTokenForCurrentThread("bjct-the-token");
        buildJobGitService.clearCloneTokenForCurrentThread();

        var credentialsProvider = (UsernamePasswordCredentialsProvider) ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");

        CredentialItem.Username username = new CredentialItem.Username();
        credentialsProvider.get(new URIish(), username);
        assertThat(username.getValue()).isEqualTo("buildjob_user");
    }

    /**
     * The one combination that cannot authenticate: no token on the job and no configured pair to fall back to. Blank
     * credentials are a valid configuration now - the intended one where every job carries a token - so this is not a
     * startup failure, and the resulting clone reports only an unauthorized response. The agent has to say which of the
     * two mechanisms is missing, or the failure names neither the absent token nor the deliberately empty property.
     */
    @Test
    void shouldStillProduceAProviderWhenNeitherMechanismIsConfigured() throws Exception {
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "");
        ReflectionTestUtils.setField(buildJobGitService, "credentialsProvider", null);
        buildJobGitService.clearCloneTokenForCurrentThread();

        var credentialsProvider = (UsernamePasswordCredentialsProvider) ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");

        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();
        credentialsProvider.get(new URIish(), username, password);

        assertThat(username.getValue()).as("a blank credential must not become an exception on the build thread").isEmpty();
        assertThat(password.getValue()).isEmpty();
    }

    /**
     * The warning that explains an unauthorizable git operation has to be emitted per operation, not once per agent
     * process. The fallback provider is built once and cached, so warning while building it would explain the first
     * affected build job and no later one - and the one that needs explaining is the build that fails hours after
     * anybody read the startup log.
     */
    @Test
    void shouldWarnOnEveryUnauthorizableOperationRatherThanOncePerProcess() {
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "");
        ReflectionTestUtils.setField(buildJobGitService, "credentialsProvider", null);
        buildJobGitService.clearCloneTokenForCurrentThread();

        var logger = (Logger) LoggerFactory.getLogger(BuildJobGitService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");
            ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");
            ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN && event.getFormattedMessage().contains("no clone token")).hasSize(3);
    }

    /**
     * The counterpart: a job that carries a token authenticates with it, so nothing is wrong and nothing may be logged.
     * A warning here would fire on every build of a correctly configured installation.
     */
    @Test
    void shouldNotWarnWhenTheJobCarriesACloneToken() {
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "");
        buildJobGitService.setCloneTokenForCurrentThread("bjct-the-token");

        var logger = (Logger) LoggerFactory.getLogger(BuildJobGitService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            ReflectionTestUtils.invokeMethod(buildJobGitService, "getCredentialsProvider");
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
            buildJobGitService.clearCloneTokenForCurrentThread();
        }

        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN).isEmpty();
    }
}
