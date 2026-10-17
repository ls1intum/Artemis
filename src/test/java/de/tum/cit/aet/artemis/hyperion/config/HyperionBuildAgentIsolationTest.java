package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

/**
 * Uses the shared standalone build-agent context, whose properties enable both Hyperion flags on purpose: an agent that inherits the core node's configuration must
 * ignore them, because every Hyperion server service needs the {@code core} profile.
 */
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
        assertThat(context.getBeanNamesForType(ChatModel.class)).isEmpty();
    }
}
