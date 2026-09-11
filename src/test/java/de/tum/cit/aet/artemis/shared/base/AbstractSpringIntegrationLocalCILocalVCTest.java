package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.LDAP_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.localci.service.TestBuildAgentConfiguration;
import de.tum.cit.aet.artemis.shared.WeaviateTestConfiguration;

// Must start up an actual web server such that the tests can communicate with the ArtemisGitServlet using JGit.
// Otherwise, only MockMvc requests could be used. The port this runs on is defined at server.port (see @TestPropertySource).
// Note: Cannot use WebEnvironment.RANDOM_PORT here because artemis.version-control.url must be set to the correct port in the @TestPropertySource annotation.
@Tag("BucketLocalCILocalVC")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ResourceLock("AbstractSpringIntegrationLocalCILocalVCTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
// NOTE: in a "single node" environment, PROFILE_BUILDAGENT must be before PROFILE_CORE to avoid issues
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_ARTEMIS, PROFILE_BUILDAGENT, PROFILE_CORE, PROFILE_SCHEDULING, PROFILE_LOCALCI, PROFILE_LOCALVC })
// Note: the server.port property must correspond to the port used in the artemis.version-control.url property.
@TestPropertySource(properties = { LDAP_ENABLED_PROPERTY_NAME + "=true", "artemis.athena.enabled=true", "artemis.apollon.enabled=false",
        "artemis.user-management.use-external=false", "artemis.sharing.enabled=true", "artemis.continuous-integration.specify-concurrent-builds=true",
        "artemis.continuous-integration.concurrent-build-size=1", "artemis.continuous-integration.asynchronous=false",
        "artemis.continuous-integration.build.images.java.default=dummy-docker-image",
        "artemis.continuous-integration.build.images.c.default=ls1tum/artemis-c-minimal-docker:1.0.0",
        "artemis.continuous-integration.build.images.c.fact=ls1tum/artemis-fact-minimal-docker:1.1.0", "artemis.continuous-integration.image-cleanup.enabled=true",
        "artemis.continuous-integration.image-cleanup.disk-space-threshold-mb=1000000000", "spring.liquibase.enabled=true", "artemis.iris.enabled=true",
        "artemis.iris.health-ttl=500", "info.contact=test@localhost", "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_localci_localvc",
        "artemis.version-control.build-agent-use-ssh=true", "artemis.version-control.ssh-private-key-folder-path=local/server-integration-test-localci/ssh-keys",
        "artemis.hyperion.enabled=true", "artemis.deimos.enabled=true", "artemis.atlas.enabled=true", "artemis.atlas.atlasml.enabled=true",
        // Use separate repo paths for LocalCI/LocalVC tests to isolate from other test buckets
        "artemis.repo-clone-path=./local/server-integration-test-localci/repos",
        "artemis.version-control.local-vcs-repo-path=./local/server-integration-test-localci/local-vcs-repos", "artemis.lti.enabled=true" })
@ContextConfiguration(classes = TestBuildAgentConfiguration.class)
public abstract class AbstractSpringIntegrationLocalCILocalVCTest extends AbstractSpringIntegrationLocalCILocalVCTestBase {

    private static final int serverPort;

    private static final int sshPort;

    private static final int hazelcastPort;

    // Static initializer runs before @DynamicPropertySource, ensuring ports are available when Spring context starts
    static {
        serverPort = findAvailableTcpPort();
        sshPort = findAvailableTcpPort();
        hazelcastPort = findAvailableTcpPort();
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> serverPort);
        registry.add("artemis.version-control.url", () -> "http://localhost:" + serverPort);
        registry.add("artemis.version-control.ssh-port", () -> sshPort);
        registry.add("artemis.version-control.ssh-template-clone-url", () -> "ssh://git@localhost:" + sshPort + "/");
        registry.add("spring.hazelcast.port", () -> hazelcastPort);

        WeaviateTestConfiguration.registerWeaviateProperties(registry, weaviateContainer, "LocalCILocalVC_");
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
