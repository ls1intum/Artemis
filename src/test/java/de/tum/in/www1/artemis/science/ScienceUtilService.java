package de.tum.in.www1.artemis.science;

import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.science.ScienceEvent;
import de.tum.in.www1.artemis.domain.science.ScienceEventType;
import de.tum.in.www1.artemis.repository.science.ScienceEventRepository;

@Service
public class ScienceUtilService {

    @Autowired
    private ScienceEventRepository scienceEventRepository;

    /**
     * Creates a science event with the passed type and resource id.
     *
     * @param principal  The login of the user associated with the event.
     * @param type       The type of the event.
     * @param resourceId The id of the resource associated with the event.
     */
    public ScienceEvent createScienceEvent(String principal, ScienceEventType type, Long resourceId) {
        ScienceEvent event = new ScienceEvent();
        event.setIdentity(principal);
        event.setTimestamp(ZonedDateTime.now());
        event.setType(type);
        event.setResourceId(resourceId);
        return scienceEventRepository.save(event);
    }
}
