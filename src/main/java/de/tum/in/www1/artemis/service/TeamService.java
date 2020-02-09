package de.tum.in.www1.artemis.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.web.rest.errors.StudentAlreadyAssignedException;

@Service
public class TeamService {

    private TeamRepository teamRepository;

    private final AuthorizationCheckService authCheckService;

    public TeamService(TeamRepository teamRepository, AuthorizationCheckService authCheckService) {
        this.teamRepository = teamRepository;
        this.authCheckService = authCheckService;
    }

    public Team save(Exercise exercise, Team team) {
        // verify that students are not assigned yet to a team for this exercise (or belong to this team itself)
        team.getStudents().forEach(student -> {
            Optional<Team> assignedTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), student.getId());
            if (assignedTeam.isPresent() && !assignedTeam.get().equals(team)) {
                throw new StudentAlreadyAssignedException(student, assignedTeam.get());
            }
        });
        team.setExercise(exercise);
        return teamRepository.save(team);
    }
}
