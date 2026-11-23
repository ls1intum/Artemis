package de.tum.cit.aet.artemis.course;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEventListener;

/** Counts real hydration only within the request thread's measurement scope. */
final class RequestEntityLoads implements AutoCloseable {

    private static final ThreadLocal<RequestEntityLoads> ACTIVE = new ThreadLocal<>();

    private static final PostLoadEventListener OBSERVER = event -> {
        var measurement = ACTIVE.get();
        if (measurement != null) {
            measurement.loadedTypes.merge(event.getEntity().getClass(), 1L, Long::sum);
        }
    };

    private final Map<Class<?>, Long> loadedTypes = new HashMap<>();

    RequestEntityLoads(EntityManagerFactory emf) {
        var listeners = emf.unwrap(SessionFactoryImplementor.class).getEventEngine().getListenerRegistry().getEventListenerGroup(EventType.POST_LOAD);
        synchronized (OBSERVER) {
            List<PostLoadEventListener> registered = new ArrayList<>();
            listeners.fireEventOnEachListener(registered, (listener, existing) -> existing.add(listener));
            if (!registered.contains(OBSERVER)) {
                listeners.appendListener(OBSERVER);
            }
        }
        ACTIVE.set(this);
    }

    long count(Class<?> entityType) {
        return loadedTypes.entrySet().stream().filter(entry -> entityType.isAssignableFrom(entry.getKey())).mapToLong(Map.Entry::getValue).sum();
    }

    long countAll() {
        return count(Object.class);
    }

    @Override
    public void close() {
        // Keep the inert shared observer installed: replacing listener groups could interrupt background callbacks.
        ACTIVE.remove();
    }
}
