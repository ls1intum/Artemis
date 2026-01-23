package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AEOLUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Batch test class for JenkinsLocalVC tests - runs in parallel with AbstractSpringIntegrationJenkinsLocalVCTest.
 * This class contains the larger/slower test classes to improve parallel test execution time.
 */
@Tag("BucketJenkinsLocalVCBatch")
@ResourceLock("AbstractSpringIntegrationJenkinsLocalVCBatchTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_LOCALVC, PROFILE_JENKINS, PROFILE_ATHENA, PROFILE_AEOLUS })
@TestPropertySource(properties = { "artemis.user-management.use-external=false",
        "artemis.user-management.course-enrollment.allowed-username-pattern=^(?!authorizationservicestudent2).*$",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_jenkins_localvc_batch", "info.contact=test@localhost",
        "artemis.continuous-integration.artemis-authentication-token-value=ThisIsAReallyLongTopSecretTestingToken",
        // Use separate paths for parallel bucket execution
        "artemis.repo-clone-path=./local/server-integration-test-batch/repos", "artemis.version-control.local-vcs-repo-path=./local/server-integration-test-batch/local-vcs-repos",
        "artemis.lti.enabled=true", "artemis.apollon.enabled=true" })
public abstract class AbstractSpringIntegrationJenkinsLocalVCBatchTest extends AbstractSpringIntegrationJenkinsLocalVCTestBase {

    private static final int serverPort;

    private static final int sshPort;

    // Static initializer runs before @DynamicPropertySource, ensuring ports are available when Spring context starts
    static {
        serverPort = findAvailableTcpPort();
        sshPort = findAvailableTcpPort();
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> serverPort);
        registry.add("artemis.version-control.url", () -> "http://localhost:" + serverPort);
        registry.add("artemis.version-control.ssh-port", () -> sshPort);
        registry.add("artemis.version-control.ssh-template-clone-url", () -> "ssh://git@localhost:" + sshPort + "/");
    }

    private static int findAvailableTcpPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not find an available TCP port", e);
        }
    }
}
