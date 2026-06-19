package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_INDEPENDENT;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.weaviate.WeaviateContainer;

import de.tum.cit.aet.artemis.shared.WeaviateTestConfiguration;
import de.tum.cit.aet.artemis.shared.WeaviateTestContainerFactory;

/**
 * This SpringBootTest is used for tests that only require a minimal set of Active Spring Profiles.
 */
@Tag("BucketIndependent")
@ResourceLock("AbstractSpringIntegrationIndependentTest")
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_TEST_INDEPENDENT, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_SCHEDULING })
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "artemis.sharing.enabled=true", "artemis.user-management.passkey.enabled=true",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_independent", "artemis.iris.enabled=true", "artemis.lti.enabled=true", "artemis.atlas.enabled=true",
        "artemis.atlas.atlasml.enabled=true", "artemis.athena.enabled=true", "artemis.apollon.enabled=true",
        // Property moved here to avoid creating a separate Spring context in AutomaticBuildJobCleanupServiceIntegrationTest
        "artemis.continuous-integration.build-job.retention-period=30",
        // Gocast integration test properties — activate GocastEnabled condition so the resource and its beans are registered;
        // GocastConnectorService and GocastApprovalLinkService are replaced with @MockitoBean in the base test class.
        "artemis.tum-live.api-base-url=http://gocast.test", "artemis.tum-live.service-account-token=test-token", "artemis.tum-live.web-base-url=http://gocast.test",
        "artemis.tum-live.service-account-user-id=999" })
public abstract class AbstractSpringIntegrationIndependentTest extends AbstractSpringIntegrationIndependentTestBase {

    protected static final WeaviateContainer weaviateContainer;

    private static final String UNIQUE_COLLECTION_PREFIX = "IntegrationIndependent_";

    static {
        weaviateContainer = WeaviateTestContainerFactory.getContainer();
    }

    @DynamicPropertySource
    static void registerWeaviateProperties(DynamicPropertyRegistry registry) {
        WeaviateTestConfiguration.registerWeaviateProperties(registry, weaviateContainer, UNIQUE_COLLECTION_PREFIX);
    }
}
