package de.tum.cit.aet.artemis.web.rest.errors;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.util.Pair;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.web.rest.TeamResource;

/**
 * Exception that will be thrown if the user tries to create a team that contains students who are already assigned to a different team for the exercise. The error response will
 * contain a list of conflicts in the format [{studentLogin, teamId}] (listing all those students for which there is a conflict and for each of them the existing team they already
 * belong to).
 */
public class StudentsAlreadyAssignedException extends BadRequestAlertException {

    @Serial
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
