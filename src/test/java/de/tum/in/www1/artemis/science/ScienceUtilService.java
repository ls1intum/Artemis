package de.tum.in.www1.artemis.science;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;

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
     * @param identity   The login of the user associated with the event.
     * @param type       The type of the event.
     * @param resourceId The id of the resource associated with the event.
     */
    public ScienceEvent createScienceEvent(String identity, ScienceEventType type, Long resourceId) {
        ScienceEvent event = new ScienceEvent();
        event.setIdentity(identity);
        event.setTimestamp(ZonedDateTime.now());
        event.setType(type);
        event.setResourceId(resourceId);
        return scienceEventRepository.save(event);
    }

    /**
     * Comparator for comparing two science events.
     * Allows for a 500ns difference between the timestamps due to the reimport from the csv export.
     */
    public static Comparator<ScienceEvent> scienceEventComparator = Comparator.comparing(ScienceEvent::getResourceId).thenComparing(ScienceEvent::getType)
            .thenComparing((ScienceEvent e1, ScienceEvent e2) -> {

                Duration d = Duration.between(e1.getTimestamp(), e2.getTimestamp());
                if (d.getNano() > 500 && d.getSeconds() >= 0) {
                    return 1;
                }
                else if (d.getSeconds() < -1 || (d.getSeconds() == -1 && d.getNano() < 999_999_500)) {
                    return -1;
                }
                return 0;
            });
}
