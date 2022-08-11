package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.in.www1.artemis.web.rest.TeamResource;

/**
 * Exception that will be thrown if the user tries to import teams that contain students whose login or registration number cannot be found. The error response will
 * contain a list of registration numbers and logins.
 */
public class StudentsNotFoundException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "studentsNotFound";

    public StudentsNotFoundException(List<String> registrationNumbers, List<String> logins) {
        super(ErrorConstants.REGISTRATION_NUMBER_NOT_FOUND_TYPE, "Users with logins or registration numbers could not be found.", TeamResource.ENTITY_NAME, ERROR_KEY,
                getParameters(registrationNumbers, logins));
    }

    private static Map<String, Object> getParameters(List<String> registrationNumbers, List<String> logins) {
        Map<String, Object> params = new HashMap<>();
        params.put("registrationNumbers", registrationNumbers);
        params.put("logins", logins);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "team.errors." + ERROR_KEY);
        parameters.put("params", params);
        return parameters;
    }
}
