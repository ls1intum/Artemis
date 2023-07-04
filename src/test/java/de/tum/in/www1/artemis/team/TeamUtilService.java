package de.tum.in.www1.artemis.team;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to teams for use in integration tests.
 */
@Service
public class TeamUtilService {

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner) {
        List<Team> teams = TeamFactory.generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, null);
        var users = teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(userUtilService::cleanUpRegistrationNumberForUser);
        userRepo.saveAll(users);
        return teamRepo.saveAll(teams);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, shortNamePrefix, "student", numberOfTeams, owner);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, "team", numberOfTeams, owner);
    }

    public List<Team> addTeamsForExerciseFixedTeamSize(String userPrefix, String regNumberPrefix, Exercise exercise, int numberOfTeams, User owner, int noOfStudentsPerTeam) {
        List<Team> teams = TeamFactory.generateTeamsForExerciseFixedTeamSize(exercise, userPrefix + "team", "student", numberOfTeams, owner, null, regNumberPrefix,
                noOfStudentsPerTeam);
        var users = teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(userUtilService::cleanUpRegistrationNumberForUser);
        userRepo.saveAll(users);
        return teamRepo.saveAll(teams);
    }

    public Team addTeamForExercise(Exercise exercise, User owner) {
        return addTeamsForExercise(exercise, 1, owner).get(0);
    }

    public Team addTeamForExercise(Exercise exercise, User owner, String loginPrefix) {
        return addTeamsForExercise(exercise, "team", loginPrefix, 1, owner).get(0);
    }

    public Team createTeam(Set<User> students, User owner, Exercise exercise, String teamName) {
        Team team = new Team();
        for (User student : students) {
            team.addStudents(student);
        }
        team.setOwner(owner);
        team.setShortName(teamName);
        team.setName(teamName);
        team.setExercise(exercise);
        return teamRepo.saveAndFlush(team);
    }
}
