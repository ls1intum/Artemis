package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.web.rest.errors.StudentsAlreadyAssignedException;

@Service
public class TeamService {

    private TeamRepository teamRepository;

    private final AuthorizationCheckService authCheckService;

    public TeamService(TeamRepository teamRepository, AuthorizationCheckService authCheckService) {
        this.teamRepository = teamRepository;
        this.authCheckService = authCheckService;
    }

    public boolean isAssignedToTeam(Exercise exercise, User user) {
        return teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId()).isPresent();
    }

    public Team save(Exercise exercise, Team team) {
        // verify that students are not assigned yet to another team for this exercise
        List<Pair<User, Team>> conflicts = findStudentTeamConflicts(exercise, team);
        if (!conflicts.isEmpty()) {
            throw new StudentsAlreadyAssignedException(conflicts);
        }
        team.setExercise(exercise);
        return teamRepository.save(team);
    }

    private List<Pair<User, Team>> findStudentTeamConflicts(Exercise exercise, Team team) {
        List<Pair<User, Team>> conflicts = new ArrayList<Pair<User, Team>>();
        team.getStudents().forEach(student -> {
            Optional<Team> assignedTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), student.getId());
            if (assignedTeam.isPresent() && !assignedTeam.get().equals(team)) {
                conflicts.add(Pair.of(student, assignedTeam.get()));
            }
        });
        return conflicts;
    }
}
