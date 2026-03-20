package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Service that renders Markdown to HTML using the same markdown-it pipeline
 * as the Angular client, executed inside GraalJS.
 * <p>
 * Thread safety: the {@link Engine} is shared (thread-safe) and caches compiled code.
 * Each render call creates a short-lived {@link Context} (NOT thread-safe) via
 * try-with-resources, avoiding ThreadLocal leaks.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MarkdownItRenderingService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownItRenderingService.class);

    private static final String BUNDLE_PATH = "graaljs/markdown-renderer.bundle.js";

    private Engine engine;

    private Source bundleSource;

    private volatile boolean available;

    /**
     * Loads the JS bundle and warms up the GraalJS engine.
     * If initialization fails, the service remains unavailable and the system falls back to CommonMark.
     */
    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource(BUNDLE_PATH);
            if (!resource.exists()) {
                log.warn("GraalJS markdown renderer bundle not found at classpath:{}", BUNDLE_PATH);
                return;
            }

            String bundleCode = resource.getContentAsString(StandardCharsets.UTF_8);
            engine = Engine.create();
            bundleSource = Source.newBuilder("js", bundleCode, "markdown-renderer.bundle.js").build();

            // Warm up: verify the bundle works in a throwaway context
            try (Context warmup = Context.newBuilder("js").engine(engine).build()) {
                warmup.eval(bundleSource);
                Value result = warmup.getBindings("js").getMember("renderMarkdown").execute("# test");
                if (result == null || !result.isString()) {
                    throw new IllegalStateException("renderMarkdown did not return a string");
                }
            }

            available = true;
            log.info("GraalJS markdown renderer initialized successfully");
        }
        catch (Exception e) {
            log.error("Failed to initialize GraalJS markdown renderer — falling back to CommonMark", e);
            available = false;
        }
    }

    /**
     * Renders the given Markdown text to HTML using the GraalJS markdown-it pipeline.
     * Creates a short-lived Context per call (the shared Engine caches compiled code,
     * so Context creation is cheap).
     *
     * @param markdown the raw Markdown text
     * @return the rendered HTML string
     * @throws IllegalStateException if the renderer is not available
     */
    public String render(String markdown) {
        if (!available) {
            throw new IllegalStateException("GraalJS markdown renderer is not available");
        }

        try (Context ctx = Context.newBuilder("js").engine(engine).build()) {
            ctx.eval(bundleSource);
            Value renderFn = ctx.getBindings("js").getMember("renderMarkdown");
            return renderFn.execute(markdown).asString();
        }
    }

    /**
     * @return true if the GraalJS renderer initialized successfully and is ready for use
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Closes the shared GraalJS engine.
     */
    @PreDestroy
    public void destroy() {
        if (engine != null) {
            try {
                engine.close();
            }
            catch (Exception e) {
                log.debug("Error closing GraalJS engine", e);
            }
        }
    }
}
