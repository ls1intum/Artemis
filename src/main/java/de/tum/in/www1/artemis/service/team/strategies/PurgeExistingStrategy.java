package de.tum.in.www1.artemis.service.team.strategies;

import java.util.List;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.team.TeamImportStrategy;

public class PurgeExistingStrategy extends TeamImportStrategy {

    private final ParticipationService participationService;

    public PurgeExistingStrategy(TeamRepository teamRepository, ParticipationService participationService) {
        super(teamRepository);
        this.participationService = participationService;
    }

    /**
     * Imports all teams of the source exercise into the destination exercise
     *
     * Conflicts are prevented trivially by first deleting all teams of the destination exercise.
     *
     * @param sourceExercise Exercise from which to take the teams for the import
     * @param destinationExercise Exercise in which to import the teams into
     */
    @Override
    public void importTeams(Exercise sourceExercise, Exercise destinationExercise) {
        // Get all source teams and clone them into the destination exercise
        List<Team> sourceTeams = teamRepository.findAllByExerciseId(sourceExercise.getId());

        deleteExistingTeamsAndAddNewTeams(destinationExercise, sourceTeams);
    }

    /**
     * Imports all given teams into the destination exercise
     *
     * Conflicts are prevented trivially by first deleting all teams of the destination exercise.
     *
     * @param exercise Exercise from which to take the teams for the import
     * @param teams Teams which to add to the exercise
     */
    @Override
    public void importTeams(Exercise exercise, List<Team> teams) {
        // Delete participations of existing teams in destination exercise (must happen before deleting teams themselves)
        deleteExistingTeamsAndAddNewTeams(exercise, teams);
    }

    /**
     * Deletes all existing teams and add given teams into the exercise
     *
     * Conflicts are prevented trivially by first deleting all teams of the destination exercise.
     *
     * @param exercise Exercise from which to take the teams for the import
     * @param teams Teams which to add to the exercise
     */
    private void deleteExistingTeamsAndAddNewTeams(Exercise exercise, List<Team> teams) {
        // Delete participations of existing teams in destination exercise (must happen before deleting teams themselves)
        participationService.deleteAllByExerciseId(exercise.getId(), false, false);

        // Purge existing teams in destination exercise
        List<Team> destinationTeams = teamRepository.findAllByExerciseId(exercise.getId());
        teamRepository.deleteAll(destinationTeams);

        cloneTeamsIntoDestinationExercise(teams, exercise);
    }
}
