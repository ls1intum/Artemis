package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

// TODO: rewrite this test to use LocalVC
@Tag("BucketJenkinsLocalVC")
@ResourceLock("AbstractSpringIntegrationJenkinsLocalVCTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_LOCALVC, PROFILE_JENKINS })
@TestPropertySource(properties = {
        // Jenkins with LocalVC is the one topology that still needs the shared credential: this context runs localvc
        // without localci, so there are no build jobs and no clone tokens, and Jenkins is not an Artemis build agent.
        // LocalVCBuildAgentCredentialsValidator refuses to start such a node without them.
        "artemis.version-control.build-agent-git-username=buildjob_user", "artemis.version-control.build-agent-git-password=buildjob_password",
        "artemis.user-management.use-external=false", "artemis.user-management.course-enrollment.allowed-username-pattern=^(?!enrollmentservicestudent2).*$",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_jenkins_localvc", "info.contact=test@localhost",
        "artemis.continuous-integration.artemis-authentication-token-value=ThisIsAReallyLongTopSecretTestingToken", "artemis.lti.enabled=true", "artemis.athena.enabled=true",
        "artemis.apollon.enabled=true" })
public abstract class AbstractSpringIntegrationJenkinsLocalVCTest extends AbstractSpringIntegrationJenkinsLocalVCTestBase {

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

        // Every test context needs its own Weaviate collection: they share the container but have separate
        // databases whose entity ids overlap, so one shared collection lets them observe each other's rows.
        de.tum.cit.aet.artemis.shared.WeaviateTestConfiguration.registerWeaviateProperties(registry, weaviateContainer, "JenkinsLocalVC_Main_");
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
