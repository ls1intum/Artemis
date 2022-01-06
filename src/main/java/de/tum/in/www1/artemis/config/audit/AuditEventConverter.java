package de.tum.in.www1.artemis.config.audit;

import java.util.*;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.util.Pair;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.PersistentAuditEvent;

@Component
public class AuditEventConverter {

    /**
     * Convert a list of {@link PersistentAuditEvent}s to a list of {@link AuditEvent}s.
     *
     * @param persistentAuditEvents the list to convert.
     * @return the converted list.
     */
    public List<AuditEvent> convertToAuditEvent(Iterable<PersistentAuditEvent> persistentAuditEvents) {
        if (persistentAuditEvents == null) {
            return Collections.emptyList();
        }
        List<AuditEvent> auditEvents = new ArrayList<>();
        for (PersistentAuditEvent persistentAuditEvent : persistentAuditEvents) {
            auditEvents.add(convertToAuditEvent(persistentAuditEvent));
        }
        return auditEvents;
    }

    /**
     * Convert a {@link PersistentAuditEvent} to an {@link AuditEvent}.
     *
     * @param persistentAuditEvent the event to convert.
     * @return the converted list.
     */
    public AuditEvent convertToAuditEvent(PersistentAuditEvent persistentAuditEvent) {
        if (persistentAuditEvent == null) {
            return null;
        }
        return new AuditEvent(persistentAuditEvent.getAuditEventDate(), persistentAuditEvent.getPrincipal(), persistentAuditEvent.getAuditEventType(),
                convertDataToObjects(persistentAuditEvent.getData()));
    }

    /**
     * Internal conversion. This is needed to support the current SpringBoot actuator AuditEventRepository interface
     *
     * @param data the data to convert
     * @return a map of String, Object
     */
    public Map<String, Object> convertDataToObjects(Map<String, String> data) {
        return data != null ? new HashMap<>(data) : new HashMap<>();
    }

    /**
     * Internal conversion. This method will allow saving additional data. By default, it will save the object as string
     *
     * @param data the data to convert
     * @return a map of String, String
     */
    public Map<String, String> convertDataToStrings(Map<String, Object> data) {
        Map<String, String> results = new HashMap<>();

        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object object = entry.getValue();

                // Extract the data that will be saved.
                if (object instanceof WebAuthenticationDetails authenticationDetails) {
                    results.put("remoteAddress", authenticationDetails.getRemoteAddress());
                    results.put("sessionId", authenticationDetails.getSessionId());
                }
                else if (object instanceof Pair authenticationPair) {
                    results.put(authenticationPair.getFirst().toString(), authenticationPair.getSecond().toString());
                }
                else {
                    results.put(entry.getKey(), Objects.toString(object));
                }
            }
        }

        return results;
    }
}
