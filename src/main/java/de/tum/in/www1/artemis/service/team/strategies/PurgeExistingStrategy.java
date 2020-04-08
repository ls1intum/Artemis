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

    @Override
    public void importTeams(Exercise sourceExercise, Exercise destinationExercise) {
        // Delete participations of existing teams in destination exercise (must happen before deleting teams themselves)
        participationService.deleteAllByExerciseId(destinationExercise.getId(), false, false);

        // Purge existing teams in destination exercise
        List<Team> destinationTeams = teamRepository.findAllByExerciseId(destinationExercise.getId());
        teamRepository.deleteAll(destinationTeams);

        // Get all source teams and clone them into the destination exercise
        List<Team> sourceTeams = teamRepository.findAllByExerciseId(sourceExercise.getId());
        cloneTeamsIntoDestinationExercise(sourceTeams, destinationExercise);
    }
}
