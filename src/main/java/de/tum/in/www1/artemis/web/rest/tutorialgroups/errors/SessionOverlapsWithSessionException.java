package de.tum.in.www1.artemis.web.rest.tutorialgroups.errors;

import java.io.Serial;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource;

/**
 * Exception that will be thrown if the user tries to create a session that overlaps with a session of the same tutorial group.
 * The error response will contain a list of overlapping sessions.
 */
public class SessionOverlapsWithSessionException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "sessionOverlapsWithSession";

    public SessionOverlapsWithSessionException(Set<TutorialGroupSession> overlappingSessions, ZoneId zoneId) {
        super(ErrorConstants.SESSION_OVERLAPS_WITH_SESSION, "Session overlaps with an existing session", TutorialGroupResource.ENTITY_NAME, ERROR_KEY,
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
