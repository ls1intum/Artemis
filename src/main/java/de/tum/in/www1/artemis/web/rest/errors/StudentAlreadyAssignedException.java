package de.tum.in.www1.artemis.web.rest.errors;

import java.util.HashMap;
import java.util.Map;

import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.web.rest.TeamResource;

public class StudentAlreadyAssignedException extends BadRequestAlertException {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_KEY = "studentAlreadyAssignedToTeam";

    public StudentAlreadyAssignedException(User student, Team team) {
        super(ErrorConstants.STUDENT_ALREADY_ASSIGNED_TYPE, "Student with login {{ studentLogin }} is already assigned to team with id {{ teamId }.", TeamResource.ENTITY_NAME,
                ERROR_KEY, getParameters(student, team));
    }

    private static Map<String, Object> getParameters(User student, Team team) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("skipAlert", true);
        parameters.put("message", "team.errors." + ERROR_KEY);
        parameters.put("params", getDetailParams(student, team));
        return parameters;
    }

    private static Map<String, Object> getDetailParams(User student, Team team) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("studentLogin", student.getLogin());
        parameters.put("teamId", team.getId());
        return parameters;
    }
}
