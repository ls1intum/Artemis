package de.tum.in.www1.artemis.web.rest.errors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.util.Pair;

import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.web.rest.TeamResource;

public class StudentsAlreadyAssignedException extends BadRequestAlertException {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "studentsAlreadyAssignedToTeams";

    public StudentsAlreadyAssignedException(List<Pair<User, Team>> conflicts) {
        super(ErrorConstants.STUDENT_ALREADY_ASSIGNED_TYPE, "Student(s) have already been assigned to other teams.", TeamResource.ENTITY_NAME, ERROR_KEY, getParameters(conflicts));
    }

    private static Map<String, Object> getParameters(List<Pair<User, Team>> conflicts) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "team.errors." + ERROR_KEY);
        parameters.put("params", getDetailParams(conflicts));
        return parameters;
    }

    private static Map<String, Object> getDetailParams(List<Pair<User, Team>> conflicts) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("conflicts", conflicts.stream().map(StudentsAlreadyAssignedException::getConflict));
        return parameters;
    }

    private static Map<String, Object> getConflict(Pair<User, Team> conflict) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("studentLogin", conflict.getFirst().getLogin());
        parameters.put("teamId", conflict.getSecond().getId());
        return parameters;
    }
}
