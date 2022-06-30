package de.tum.in.www1.artemis.service.team;

import java.util.List;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.repository.TeamRepository;

public abstract class TeamImportStrategy {

    protected final TeamRepository teamRepository;

    public TeamImportStrategy(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    abstract public void importTeams(Exercise sourceExercise, Exercise destinationExercise);

    abstract public void importTeams(Exercise exercise, List<Team> teams);

    /**
     * Clones the given original teams via copy constructor, assigns them to the given destination exercise and persists them
     *
     * @param originalTeams Team that should be cloned
     * @param destinationExercise Exercise in which the cloned teams should be saved
     */
    protected void cloneTeamsIntoDestinationExercise(List<Team> originalTeams, Exercise destinationExercise) {
        List<Team> clonedTeams = originalTeams.stream().map(Team::new).map(team -> team.exercise(destinationExercise)).toList();
        teamRepository.saveAll(clonedTeams);
    }
}
