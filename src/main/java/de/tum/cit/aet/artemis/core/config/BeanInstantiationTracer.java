package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.util.Pair;

@Component
@Profile(SPRING_PROFILE_DEVELOPMENT)
@Lazy
public class BeanInstantiationTracer implements InstantiationAwareBeanPostProcessor, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(BeanInstantiationTracer.class);

    private static final String BASE = "de.tum.cit.aet.artemis";

    private final ThreadLocal<Deque<String>> callStack = ThreadLocal.withInitial(ArrayDeque::new);

    private final Queue<Pair<String, String>> edges = new ConcurrentLinkedQueue<>();

    @Override
    public Object postProcessBeforeInstantiation(Class<?> cls, String name) {
        if (cls.getName().startsWith(BASE)) {
            Deque<String> stack = callStack.get();
            String parent = stack.peek();
            if (parent != null) {
                edges.add(new Pair<>(parent, name));
            }
            log.info("Instantiating: {} ← {}", name, parent != null ? parent : "root");
            stack.push(name);
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

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // e.g. write to src/main/resources/beans.dot
        try (PrintWriter out = new PrintWriter("beans.dot")) {
            out.println("digraph beans {");
            for (Pair<String, String> edge : edges) {
                out.printf("  \"%s\" -> \"%s\";%n", edge.first(), edge.second());
            }
            out.println("}");
            log.info("Bean instantiation graph exported to beans.dot ({} edges)", edges.size());
        }
        catch (IOException e) {
            log.error("Failed to write beans.dot", e);
        }
    }
}
