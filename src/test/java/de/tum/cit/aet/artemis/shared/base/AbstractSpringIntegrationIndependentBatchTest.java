package de.tum.cit.aet.artemis.shared.base;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ARTEMIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_INDEPENDENT;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Second independent integration-test bucket. It uses the same Spring profiles as {@link AbstractSpringIntegrationIndependentTest}, but separate filesystem paths so it can
 * run in parallel with the main independent bucket.
 */
@Tag("BucketIndependentBatch")
@ResourceLock("AbstractSpringIntegrationIndependentBatchTest")
@ActiveProfiles({ SPRING_PROFILE_TEST, PROFILE_TEST_INDEPENDENT, PROFILE_ARTEMIS, PROFILE_CORE, PROFILE_SCHEDULING })
@TestPropertySource(properties = { "artemis.user-management.use-external=false", "artemis.sharing.enabled=true", "artemis.user-management.passkey.enabled=true",
        "spring.jpa.properties.hibernate.cache.hazelcast.instance_name=Artemis_independent_batch", "artemis.iris.enabled=true", "artemis.lti.enabled=true",
        "artemis.atlas.enabled=true", "artemis.atlas.atlasml.enabled=true", "artemis.athena.enabled=true", "artemis.apollon.enabled=true",
        "artemis.continuous-integration.build-job.retention-period=30",
        // Use separate paths for parallel bucket execution.
        "artemis.course-archives-path=./local/server-integration-test-independent-batch/exports/courses",
        "artemis.legal-path=./local/server-integration-test-independent-batch/legal", "artemis.repo-clone-path=./local/server-integration-test-independent-batch/repos",
        "artemis.repo-download-clone-path=./local/server-integration-test-independent-batch/repos-download",
        "artemis.data-export-path=./local/server-integration-test-independent-batch/data-exports",
        "artemis.build-logs-path=./local/server-integration-test-independent-batch/build-logs",
        "artemis.file-upload-path=./local/server-integration-test-independent-batch/uploads",
        "artemis.submission-export-path=./local/server-integration-test-independent-batch/exports",
        "artemis.checked-out-repos-path=./local/server-integration-test-independent-batch/checked-out-repos",
        "artemis.version-control.local-vcs-repo-path=./local/server-integration-test-independent-batch/local-vcs-repos",
        "artemis.version-control.ssh-private-key-folder-path=./local/server-integration-test-independent-batch/ssh-keys",
        "artemis.temp-path=./local/server-integration-test-independent-batch/temp",
        // Load the shared read-only CSV seed (config/liquibase/e2e/*.csv) so tests in this bucket can reference seeded
        // users/courses/exercises instead of creating them. See de.tum.cit.aet.artemis.shared.SeedData.
        "spring.liquibase.contexts=tests,seed" })
public abstract class AbstractSpringIntegrationIndependentBatchTest extends AbstractSpringIntegrationIndependentTestBase {
}
