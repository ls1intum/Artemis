package de.tum.in.www1.artemis.service.team;

import java.util.List;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.repository.TeamRepository;

public abstract class TeamImportStrategy {

    protected final TeamRepository teamRepository;

    public TeamImportStrategy(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    abstract public void importTeams(Exercise sourceExercise, Exercise destinationExercise);

    protected void cloneTeamsIntoDestinationExercise(List<Team> originalTeams, Exercise destinationExercise) {
        List<Team> clonedTeams = originalTeams.stream().map(Team::new).map(team -> team.exercise(destinationExercise)).collect(Collectors.toList());
        teamRepository.saveAll(clonedTeams);
    }
}
