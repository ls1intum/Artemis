package de.tum.in.www1.artemis.service.science;

import java.security.Principal;
import java.time.ZonedDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.science.ScienceEvent;
import de.tum.in.www1.artemis.domain.science.ScienceEventType;
import de.tum.in.www1.artemis.repository.science.ScienceEventRepository;

/**
 * Service class for {@link ScienceEvent}.
 */
@Service
public class ScienceEventService {

    private final ScienceEventRepository scienceEventRepository;

    public ScienceEventService(ScienceEventRepository scienceEventRepository) {
        this.scienceEventRepository = scienceEventRepository;
    }

    /**
     * Logs the event for the current principle with the current timestamp.
     *
     * @param type the type of the event that should be logged
     */
    public void logEvent(ScienceEventType type) {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final Principal principal = (Principal) auth.getPrincipal();
        logEvent(type, principal);
    }

    /**
     * Logs the event for the given principal with the current timestamp.
     *
     * @param type      the type of the event that should be logged
     * @param principal the principal for whom the event should be logged
     */
    private void logEvent(ScienceEventType type, Principal principal) {
        logEvent(type, principal, ZonedDateTime.now());
    }

    /**
     * Logs the event for the given principal with the given timestamp.
     *
     * @param type      the type of the event that should be logged
     * @param principal the principal for whom the event should be logged
     * @param timestamp the time when the event happened
     */
    private void logEvent(ScienceEventType type, Principal principal, ZonedDateTime timestamp) {
        ScienceEvent event = new ScienceEvent();
        event.setIdentity(principal.hashCode());
        event.setTimestamp(ZonedDateTime.now());
        event.setType(type);

        scienceEventRepository.save(event);
    }
}
