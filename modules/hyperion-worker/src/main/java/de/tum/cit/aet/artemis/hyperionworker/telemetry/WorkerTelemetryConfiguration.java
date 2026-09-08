package de.tum.cit.aet.artemis.hyperionworker.telemetry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Content export is an explicit worker-side opt-in, independent of the core server. */
@Configuration(proxyBeanMethods = false)
public class WorkerTelemetryConfiguration {

    @Bean
    ChatModelContentObservationFilter chatModelContentObservationFilter(@Value("${artemis.telemetry.gen-ai.capture-content:false}") boolean captureContent,
            @Value("${artemis.telemetry.gen-ai.max-attribute-bytes:2000000}") int maxAttributeBytes) {
        return new ChatModelContentObservationFilter(new ObjectMapper(), captureContent, maxAttributeBytes);
    }
}
