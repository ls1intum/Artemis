package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

/** Typed Hyperion payload mapping; transport is owned by AI Worker. */
@Lazy
@Configuration
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionWorkerMessagingConfiguration {

    @Bean
    @Lazy
    public WorkerMessageCodec hyperionWorkerMessageCodec() {
        return new WorkerMessageCodec();
    }
}
