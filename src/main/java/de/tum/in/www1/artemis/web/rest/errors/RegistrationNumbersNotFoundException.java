package de.tum.in.www1.artemis.web.rest.errors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.in.www1.artemis.web.rest.TeamResource;

/**
 * Exception that will be thrown if the user tries to imports teams that contains students whose registration number cannot be found. The error response will
 * contain a list of registration numbers.
 */
public class RegistrationNumbersNotFoundException extends BadRequestAlertException {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "registrationNumbersNotFound";

    public RegistrationNumbersNotFoundException(List<String> registrationNumbers) {
        super(ErrorConstants.REGISTRATION_NUMBER_NOT_FOUND, "Users with registration numbers could not be found.", TeamResource.ENTITY_NAME, ERROR_KEY,
                getParameters(registrationNumbers));
    }

    private static Map<String, Object> getParameters(List<String> registrationNumbers) {
        Map<String, Object> registrationParams = new HashMap<>();
        registrationParams.put("registrationNumbers", registrationNumbers);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "team.errors." + ERROR_KEY);
        parameters.put("params", registrationParams);
        return parameters;
    }
}
