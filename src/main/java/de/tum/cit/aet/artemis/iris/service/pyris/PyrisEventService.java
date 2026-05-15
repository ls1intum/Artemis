package de.tum.cit.aet.artemis.iris.service.pyris;

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
     *
     * @param event The event object received to trigger the matching action
     * @throws UnsupportedPyrisEventException if the event is not supported
     *
     * @see PyrisEvent
     */
    @Async
    public void trigger(PyrisEvent<?> event) {
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
    }
}
