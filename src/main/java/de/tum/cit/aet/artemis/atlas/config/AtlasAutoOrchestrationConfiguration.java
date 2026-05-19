package de.tum.cit.aet.artemis.atlas.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Beans for the automatic competency orchestration pipeline (accumulator + scheduler). Kept in a
 * dedicated configuration so the orchestrator's main service surface and the chat agent stay
 * untouched — the auto-trigger feature ships independently.
 */
@Conditional(AtlasEnabled.class)
@Configuration
public class AtlasAutoOrchestrationConfiguration {

    /**
     * System clock used by the content-change accumulator and scheduler. Registered only when no
     * other {@link Clock} bean is present so tests can override with a fixed or tick clock to
     * fast-forward across the debounce window and day boundaries without sleeping.
     *
     * @return a default system clock used by the auto-orchestration components
     */
    @Bean
    @Lazy
    @ConditionalOnMissingBean
    public Clock atlasAutoOrchestrationClock() {
        return Clock.systemDefaultZone();
    }
}
