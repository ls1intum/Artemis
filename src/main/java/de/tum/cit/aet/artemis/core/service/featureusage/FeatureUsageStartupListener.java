package de.tum.cit.aet.artemis.core.service.featureusage;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Triggers the feature inventory scan once the application is up.
 * <p>
 * Exists only to keep the eager part of this feature as small as possible. A lazily created bean cannot carry an
 * {@code @EventListener}, because Spring has to instantiate the bean to register the listener, so the scan needs something
 * eager. Putting the listener on {@link FeatureUsageRegistry} itself made that registry eager, which dragged
 * {@code TrackedFeatureRepository} and the whole JPA infrastructure behind it into the startup dependency graph and pushed
 * the longest startup chain past the limit the bean instantiation check enforces.
 * <p>
 * This class depends on nothing but the context, so it costs one node in that graph. The registry is resolved when the
 * event fires, which is after startup, so its own dependencies never become part of it.
 */
@Profile(PROFILE_CORE)
@Component
// eager, so the listener below is registered before the application ready event fires
@Lazy(false)
public class FeatureUsageStartupListener {

    private final ApplicationContext applicationContext;

    public FeatureUsageStartupListener(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerFeatureInventory() {
        applicationContext.getBean(FeatureUsageRegistry.class).registerEndpoints();
    }
}
