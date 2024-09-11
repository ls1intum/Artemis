package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.util.Pair;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.web.TeamResource;

/**
 * Exception that will be thrown if the user tries to import teams that contain students who appear in another imported team. The error response will
 * contain a list of login-registration number pairs.
 */
public class StudentsAppearMultipleTimesException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "studentsAppearMultipleTimes";

    public StudentsAppearMultipleTimesException(List<User> students) {
        super(ErrorConstants.STUDENTS_APPEAR_MULTIPLE_TIMES_TYPE, "Students appear multiple times in team import request.", TeamResource.ENTITY_NAME, ERROR_KEY,
                getParameters(students));
    }

    private static Map<String, Object> getParameters(List<User> students) {
        Map<String, List<Pair<String, String>>> params = new HashMap<>();
        params.put("students", students.stream().map(student -> Pair.of(student.getLogin(), student.getRegistrationNumber())).toList());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "team.errors." + ERROR_KEY);
        parameters.put("params", params);
        return parameters;
    }
}
