package de.tum.cit.aet.artemis.metis.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import de.tum.cit.aet.artemis.metis.config.MetisProperties;

/**
 * Builds {@link MetisProperties} for unit tests the way the application binds them: through Spring Boot's binder, so
 * that every key not given keeps the default declared on the record, and strictly, so that a misspelled key fails the
 * test instead of silently testing the default.
 */
public final class MetisTestProperties {

    private static final String PREFIX = "artemis.metis";

    private MetisTestProperties() {
    }

    /**
     * The configuration a node gets without any {@code artemis.metis} keys.
     *
     * @return the properties with every default
     */
    public static MetisProperties defaults() {
        return with(Map.of());
    }

    /**
     * The default configuration with some keys overridden.
     *
     * @param relativeKeys values by key relative to {@code artemis.metis}, e.g. {@code llm.max-concurrent-calls}
     * @return the bound properties
     */
    public static MetisProperties with(Map<String, String> relativeKeys) {
        Map<String, String> source = new HashMap<>();
        relativeKeys.forEach((key, value) -> source.put(PREFIX + "." + key, value));
        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bindOrCreate(PREFIX, Bindable.of(MetisProperties.class), new NoUnboundElementsBindHandler(BindHandler.DEFAULT));
    }
}
