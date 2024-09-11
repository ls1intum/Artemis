package de.tum.cit.aet.artemis.service.team;

import java.util.List;

import org.hibernate.Hibernate;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;

public abstract class TeamImportStrategy {

    protected final TeamRepository teamRepository;

    public TeamImportStrategy(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public abstract void importTeams(Exercise sourceExercise, Exercise destinationExercise);

    public abstract void importTeams(Exercise exercise, List<Team> teams);

    /**
     * Clones the given original teams via copy constructor, assigns them to the given destination exercise and persists them
     *
     * @param originalTeams       Team that should be cloned
     * @param destinationExercise Exercise in which the cloned teams should be saved
     */
    protected void cloneTeamsIntoDestinationExercise(List<Team> originalTeams, Exercise destinationExercise) {
        List<Team> clonedTeams = originalTeams.stream().map(team -> {
            if (!Hibernate.isInitialized(team.getStudents())) {
                return teamRepository.findWithStudentsByIdElseThrow(team.getId());
            }
            return team;
        }).map(Team::new).map(team -> team.exercise(destinationExercise)).toList();
        teamRepository.saveAll(clonedTeams);
    }
}
