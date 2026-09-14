package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

/** Uses the same standalone server context as the LocalCI build-agent integration tests. */
@TestPropertySource(properties = { "artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true" })
class HyperionBuildAgentIsolationTest extends AbstractArtemisBuildAgentTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void generationFlagsCannotTurnStandaloneBuildAgentIntoCore() {
        assertThat(context.getBeanNamesForType(BuildAgentConfiguration.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(HyperionWorkerMessagingConfiguration.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GenerationWorkerClientService.class)).isEmpty();
        assertThat(context.containsBean("hyperionConnectionFactory")).isFalse();
        assertThat(context.containsBean("entityManagerFactory")).isFalse();
        assertThat(context.containsBean("dataSource")).isFalse();
        assertThat(context.containsBean("hyperionWebsocketService")).isFalse();
        assertThat(context.containsBean("exerciseVariantJobService")).isFalse();
    }
}
