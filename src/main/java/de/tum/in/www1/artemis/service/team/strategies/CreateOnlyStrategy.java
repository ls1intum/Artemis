package de.tum.in.www1.artemis.service.team.strategies;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.service.team.TeamImportStrategy;

public class CreateOnlyStrategy extends TeamImportStrategy {

    public CreateOnlyStrategy(TeamRepository teamRepository) {
        super(teamRepository);
    }

    @Override
    public void importTeams(Exercise sourceExercise, Exercise destinationExercise) {
        // Filter the source teams and only clone the conflict-free teams into the destination exercise
        List<Team> conflictFreeSourceTeams = getExerciseTeamsAndFindConflictFreeSourceTeams(sourceExercise, destinationExercise);
        cloneTeamsIntoDestinationExercise(conflictFreeSourceTeams, destinationExercise);
    }

    @Override
    public void importTeams(Exercise exercise, List<Team> teams) {
        // Filter the source teams and only clone the conflict-free teams into the destination exercise
        List<Team> conflictFreeSourceTeams = getExerciseTeamsAndFindConflictFreeSourceTeams(exercise, teams);
        cloneTeamsIntoDestinationExercise(conflictFreeSourceTeams, exercise);
    }

    /**
     * Filters the teams from the given source exercise and returns only those that can be imported into the destination exercise without conflicts
     *
     * Conditions for being conflict-free:
     * 1. No clash in team short name
     * 2. No overlapping students
     *
     * @param sourceExercise Exercise from which to take the teams for the import
     * @param destinationExercise Exercise in which to import the teams into
     * @return an unmodifiable list of those source teams that have no conflicts
     */
    private List<Team> getExerciseTeamsAndFindConflictFreeSourceTeams(Exercise sourceExercise, Exercise destinationExercise) {
        // Get all teams from the source exercise and from the destination exercise
        List<Team> sourceTeams = teamRepository.findAllByExerciseId(sourceExercise.getId());
        List<Team> destinationTeams = teamRepository.findAllByExerciseId(destinationExercise.getId());

        return getConflictFreeTeams(destinationTeams, sourceTeams);
    }

    /**
     * Filters the teams from the given source exercise and returns only those that can be imported into the destination exercise without conflicts
     *
     * Conditions for being conflict-free:
     * 1. No clash in team short name
     * 2. No overlapping students
     *
     * @param exercise Exercise from which to take the teams for the import
     * @param teams Teams which will be added into exercise
     * @return an unmodifiable list of those source teams that have no conflicts
     */
    private List<Team> getExerciseTeamsAndFindConflictFreeSourceTeams(Exercise exercise, List<Team> teams) {
        // Get all teams from the given exercise
        List<Team> existingTeams = teamRepository.findAllByExerciseId(exercise.getId());

        return getConflictFreeTeams(existingTeams, teams);
    }

    /**
     * Filters the teams from the given team list and returns only those that do not have conflicts with the existing ones
     *
     * Conditions for being conflict-free:
     * 1. No clash in team short name
     * 2. No overlapping students
     *
     * @param existingTeams Teams that are already in the exercise
     * @param newTeams Teams which will be added into exercise
     * @return an unmodifiable list of those source teams that have no conflicts
     */
    private List<Team> getConflictFreeTeams(List<Team> existingTeams, List<Team> newTeams) {
        Set<String> existingTeamShortNames = existingTeams.stream().map(Team::getShortName).collect(Collectors.toSet());
        Set<User> existingTeamStudents = existingTeams.stream().flatMap(team -> team.getStudents().stream()).collect(Collectors.toSet());

        // Filter for conflict-free source teams (1. no short name conflict, 2. no student overlap)
        Stream<Team> conflictFreeSourceTeams = newTeams.stream().filter(newTeam -> {
            final boolean noShortNameConflict = !existingTeamShortNames.contains(newTeam.getShortName());
            final boolean noStudentConflict = Collections.disjoint(existingTeamStudents, newTeam.getStudents());
            return noShortNameConflict && noStudentConflict;
        });

        return conflictFreeSourceTeams.toList();
    }
}
