package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisEvent;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;

/**
 * Service to handle Pyris events.
 * <p>
 * Validates incoming events and republishes them through Spring's {@link ApplicationEventPublisher}.
 * Handlers register via {@code @EventListener} (see {@link IrisChatSessionService#handleNewResultEvent}),
 * which keeps this service decoupled from concrete consumers and avoids a deep constructor-injection chain
 * into the chat session and pipeline services.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisEventService {

    private static final Logger log = LoggerFactory.getLogger(PyrisEventService.class);

    private final ApplicationEventPublisher eventPublisher;

    public PyrisEventService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Validates and republishes the given {@link PyrisEvent} as a Spring application event so that
     * registered {@code @EventListener}s can react to it.
     * <p>
     * Runs asynchronously. Production callers use this fire-and-forget and ignore the returned future; it exists so
     * that callers which must observe the effect of the dispatch (notably tests) can wait for the publication to have
     * finished instead of polling for its side effects with an arbitrary deadline. Failures are logged here, so an
     * ignored future never hides an error.
     *
     * @param event The event object received to trigger the matching action
     * @return a future that completes once the event has been published (exceptionally if the event is not supported)
     * @throws UnsupportedPyrisEventException if the event is not supported
     *
     * @see PyrisEvent
     */
    @Async
    public CompletableFuture<Void> trigger(PyrisEvent<?> event) {
        log.debug("Starting to process event of type: {}", event.getClass().getSimpleName());
        try {
            switch (event) {
                case NewResultEvent newResultEvent -> {
                    log.debug("Publishing NewResultEvent: {}", newResultEvent);
                    eventPublisher.publishEvent(newResultEvent);
                    log.debug("Successfully published NewResultEvent");
                }
                default -> throw new UnsupportedPyrisEventException("Unsupported event type: " + event.getClass().getSimpleName());
            }
        }
        catch (Exception e) {
            log.error("Failed to process event: {}", event, e);
            throw e;
        }
        return CompletableFuture.completedFuture(null);
    }
}
