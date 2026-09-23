package de.tum.cit.aet.artemis.hyperion.service.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationStateChangedEvent;

class HyperionWebsocketServiceTest {

    @Test
    void publishesExerciseStateToItsSharedTopic() {
        var messaging = mock(WebsocketMessagingService.class);
        var state = new ExerciseGenerationStateDTO(42, "job-1", true);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("core");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("hyperion-test", Map.of("artemis.hyperion.enabled", "true")));
            context.registerBean(HyperionWebsocketService.class, () -> new HyperionWebsocketService(messaging));
            context.refresh();
            context.publishEvent(new ExerciseGenerationStateChangedEvent(state));
        }

        verify(messaging).sendMessage("/topic/hyperion/exercise-generation/exercises/42/state", state);
    }
}
