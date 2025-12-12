package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.PrintStartupBeansEvent;
import de.tum.cit.aet.artemis.core.util.Pair;

@Component
@Profile(SPRING_PROFILE_DEVELOPMENT)
@Lazy
public class BeanInstantiationTracer implements InstantiationAwareBeanPostProcessor {

    // Keep these two constants in sync with the values in .github/workflows/bean-instantiations.yml
    private static final int STARTUP_MAX_DEPENDENCY_CHAIN_THRESHOLD = 10;

    private static final int DEFERRED_INIT_MAX_DEPENDENCY_CHAIN_THRESHOLD = 16;

    private static final Logger log = LoggerFactory.getLogger(BeanInstantiationTracer.class);

    private static final String BASE = "de.tum.cit.aet.artemis";

    private final ThreadLocal<Deque<String>> callStack = ThreadLocal.withInitial(ArrayDeque::new);

    private final Queue<Pair<String, String>> edges = new ConcurrentLinkedQueue<>();

    private final AtomicInteger maxCallStackSize = new AtomicInteger(0);

    private final AtomicReference<List<String>> longestChain = new AtomicReference<>(new ArrayList<>());

    private final Queue<List<String>> exceedingThresholdChains = new ConcurrentLinkedQueue<>();

    private final Queue<List<String>> deferredInstantiationExceedingThresholdChains = new ConcurrentLinkedQueue<>();

    @Override
    public Object postProcessBeforeInstantiation(Class<?> cls, String name) {
        if (cls.getName().startsWith(BASE)) {
            Deque<String> stack = callStack.get();
            String parent = stack.peek();
            if (parent != null) {
                edges.add(new Pair<>(parent, name));
            }
            stack.push(name);

            int depth = stack.size();

            if (depth > STARTUP_MAX_DEPENDENCY_CHAIN_THRESHOLD) {
                exceedingThresholdChains.add(new ArrayList<>(stack));
            }
            if (depth > DEFERRED_INIT_MAX_DEPENDENCY_CHAIN_THRESHOLD) {
                deferredInstantiationExceedingThresholdChains.add(new ArrayList<>(stack));
            }

            int prevMax = maxCallStackSize.get();
            if (depth > prevMax && maxCallStackSize.compareAndSet(prevMax, depth)) {
                longestChain.set(new ArrayList<>(stack));
            }
        }
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean.getClass().getName().startsWith(BASE)) {
            Deque<String> stack = callStack.get();
            if (!stack.isEmpty() && stack.peek().equals(name)) {
                stack.pop();
            }
        }
        return bean;
    }

    /**
     * Prints the bean instantiation graph to a DOT file that can be visualized on <a href="http://www.webgraphviz.com/">GraphViz</a> when the application starts up.
     * The event is only published when the development profile is active.
     * This is useful for debugging and performance improvements, but should not be enabled in production environments
     */
    @EventListener(PrintStartupBeansEvent.class)
    public void printDependencyGraph() {
        try (PrintWriter out = new PrintWriter("startupBeans.dot")) {
            out.println("digraph beans {");
            for (Pair<String, String> edge : edges) {
                out.printf("  \"%s\" -> \"%s\";%n", edge.first(), edge.second());
            }
            out.println("}");
            log.debug("Bean instantiation graph exported to startupBeans.dot ({} edges, longest dependency chain length: {})", edges.size(), longestChain.get().size());
        }
        catch (IOException e) {
            log.error("Failed to write startupBeans.dot", e);
        }

        int i = 1;
        for (List<String> chain : exceedingThresholdChains) {
            List<String> reversed = new ArrayList<>(chain);
            Collections.reverse(reversed);
            log.debug("Startup long bean instantiation chain {} (length {}): {}", i++, reversed.size(), String.join(" → ", reversed));
        }

        List<String> longest = longestChain.get();
        if (!longest.isEmpty()) {
            List<String> forward = new ArrayList<>(longest);
            Collections.reverse(forward);
            log.debug("Longest instantiation chain: {}", String.join(" → ", forward));
        }
        // Clear startup data so deferred-eager init is measured independently
        exceedingThresholdChains.clear();
        deferredInstantiationExceedingThresholdChains.clear();
        edges.clear();
        maxCallStackSize.set(0);
        longestChain.set(new ArrayList<>());
    }

    /**
     * Logs the deferred bean instantiation chains that exceed the threshold after the deferred eager bean initialization is completed.
     */
    @EventListener(DeferredEagerBeanInitializationCompletedEvent.class)
    public void logDeferredInitChainsExceedingThreshold() {
        String filename = "deferredEagerBeanInstantiationViolations.dot";
        try (PrintWriter out = new PrintWriter(filename)) {
            out.println("digraph beans {");

            int i = 1;
            for (List<String> chain : deferredInstantiationExceedingThresholdChains) {
                List<String> reversed = new ArrayList<>(chain);
                Collections.reverse(reversed);

                log.debug("Deferred long bean instantiation chain {} (length {}): {}", i++, reversed.size(), String.join(" → ", reversed));

                for (int j = 0; j < reversed.size() - 1; j++) {
                    out.printf("  \"%s\" -> \"%s\";%n", reversed.get(j), reversed.get(j + 1));
                }
            }

            out.println("}");
        }
        catch (IOException e) {
            log.error("Failed to write {} ", filename, e);
        }

        log.debug("Maximum dependency chain length during deferred eager init: {}", maxCallStackSize.get());
    }
}
