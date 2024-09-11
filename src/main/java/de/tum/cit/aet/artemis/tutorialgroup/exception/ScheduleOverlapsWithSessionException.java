package de.tum.cit.aet.artemis.tutorialgroup.exception;

import java.io.Serial;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.tutorialgroup.web.TutorialGroupResource;

/**
 * Exception that will be thrown if the user tries to create a tutorial group with a schedule that overlaps with a session of the same tutorial group.
 * The error response will contain a list of overlapping sessions.
 */
public class ScheduleOverlapsWithSessionException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "scheduleOverlapsWithSession";

    public ScheduleOverlapsWithSessionException(Set<TutorialGroupSession> overlappingSessions, ZoneId zoneId) {
        super(ErrorConstants.SCHEDULE_OVERLAPS_WITH_SESSION, "Schedule overlaps with an existing session", TutorialGroupResource.ENTITY_NAME, ERROR_KEY,
                getParameters(overlappingSessions, zoneId));
    }

    private static Map<String, Object> getParameters(Set<TutorialGroupSession> overlappingSessions, ZoneId zoneId) {
        Map<String, List<String>> params = new HashMap<>();
        params.put("sessions", overlappingSessions.stream().map(session -> session.getStart().withZoneSameInstant(zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE)).toList());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "artemisApp.errors." + ERROR_KEY);
        parameters.put("params", params);
        return parameters;
    }

}
