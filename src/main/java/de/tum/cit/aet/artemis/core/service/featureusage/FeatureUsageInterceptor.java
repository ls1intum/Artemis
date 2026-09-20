package de.tum.cit.aet.artemis.core.service.featureusage;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Counts one use of a feature per API request.
 * <p>
 * A {@link HandlerInterceptor} rather than a filter, because only here is the resolved handler method available, and the
 * handler method is what identifies the feature. It is also what bounds the data: the set of handler methods is fixed at
 * roughly a thousand, whereas a filter would see raw paths. That distinction is not theoretical. Micrometer's
 * {@code http.server.requests} exhausts even a raised URI tag budget in production, because the LocalVC git servlet
 * bypasses Spring MVC and every repository path becomes its own tag value.
 * <p>
 * The clock and the role are read in {@code preHandle} rather than in {@code afterCompletion}, so an asynchronous
 * dispatch that completes on a different thread still reports the right role and the full duration.
 * <p>
 * Registered ahead of the other interceptors in {@code WebConfigurer}, so a request that a later interceptor rejects is
 * still counted, as an error. Requests rejected earlier, by the security filter chain, never reach any interceptor and
 * are therefore not counted at all: the error count measures failures of the feature, not failed attempts to reach it.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class FeatureUsageInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageInterceptor.class);

    private static final String START_NANOS_ATTRIBUTE = FeatureUsageInterceptor.class.getName() + ".startNanos";

    private static final String CALLER_ROLE_ATTRIBUTE = FeatureUsageInterceptor.class.getName() + ".callerRole";

    private final FeatureUsageProperties properties;

    /**
     * The collector is resolved on first use rather than injected.
     * <p>
     * {@code WebConfigurer} constructor-injects this interceptor and has to exist before the web server does, so injecting
     * the collector here would put it and everything behind it on the startup dependency path. That pushed the longest startup chain past the limit the bean instantiation check
     * enforces. Marking the
     * parameter {@code @Lazy} is not an option either, forbidden by
     * {@code ArchitectureTest.ensureLazyAnnotationNotUsedOnParameters}.
     */
    private final ApplicationContext applicationContext;

    private volatile FeatureUsageCollector collector;

    public FeatureUsageInterceptor(FeatureUsageProperties properties, ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    private FeatureUsageCollector collector() {
        FeatureUsageCollector resolved = collector;
        if (resolved == null) {
            resolved = applicationContext.getBean(FeatureUsageCollector.class);
            collector = resolved;
        }
        return resolved;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled()) {
            return true;
        }
        // Guarded like afterCompletion below. Both run synchronously on the request thread, so anything thrown here would
        // fail the request itself - and this one runs before the handler, so it would fail it outright rather than after
        // the work was already done. The class contract is that recording never propagates a failure; this is half of it.
        try {
            // Spring runs preHandle again on the ASYNC dispatch of an asynchronous request, so the values of the first
            // dispatch are kept. Overwriting them would report only the second dispatch as the duration and re-read the
            // role from a security context that the async dispatch does not necessarily carry.
            if (request.getAttribute(START_NANOS_ATTRIBUTE) == null) {
                request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
                request.setAttribute(CALLER_ROLE_ATTRIBUTE, SecurityUtils.getCurrentUserHighestRole());
            }
        }
        catch (Exception e) {
            log.debug("Failed to start measuring a request", e);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        if (!properties.enabled() || !(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        if (!(request.getAttribute(START_NANOS_ATTRIBUTE) instanceof Long startNanos)) {
            return;
        }
        // Nothing here may affect the request it measures. Everything read from the request or the response is read on
        // this thread, because a container may recycle both the moment the request completes; everything else, including
        // resolving the feature from its handler method, happens on the recording thread. The block stays guarded
        // because resolving the collector bean on the very first request happens out here.
        try {
            Role callerRole = request.getAttribute(CALLER_ROLE_ATTRIBUTE) instanceof Role role ? role : Role.ANONYMOUS;
            boolean failed = ex != null || response.getStatus() >= HttpServletResponse.SC_BAD_REQUEST;
            collector().recordRestUsage(handlerMethod.getMethod(), callerRole, failed, (System.nanoTime() - startNanos) / 1_000_000);
        }
        catch (Exception e) {
            log.debug("Failed to record usage of {}", handlerMethod.getMethod(), e);
        }
    }
}
