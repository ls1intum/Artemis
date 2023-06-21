package de.tum.in.www1.artemis.team;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
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

    /**
     * Generate a team
     *
     * @param exercise           exercise of the team
     * @param name               name of the team
     * @param shortName          short name of the team
     * @param loginPrefix        prefix that will be added in front of every user's login
     * @param numberOfStudents   amount of users to generate for team as students
     * @param owner              owner of the team generally a tutor
     * @param creatorLogin       login of user that creates the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @return team that was generated
     */
    public Team generateTeamForExercise(Exercise exercise, String name, String shortName, String loginPrefix, int numberOfStudents, User owner, String creatorLogin,
            String registrationPrefix) {
        List<User> students = userUtilService.generateActivatedUsersWithRegistrationNumber(shortName + loginPrefix, new String[] { "tumuser", "testgroup" },
                Set.of(new Authority(Role.STUDENT.getAuthority())), numberOfStudents, registrationPrefix);

        Team team = new Team();
        team.setName(name);
        team.setShortName(shortName);
        team.setExercise(exercise);
        team.setStudents(new HashSet<>(students));
        if (owner != null) {
            team.setOwner(owner);
        }
        if (creatorLogin != null) {
            team.setCreatedBy(creatorLogin);
            team.setLastModifiedBy(creatorLogin);
        }
        return team;
    }

    /**
     * Generate a team
     *
     * @param exercise         exercise of the team
     * @param name             name of the team
     * @param shortName        short name of the team
     * @param numberOfStudents amount of users to generate for team as students
     * @param owner            owner of the team generally a tutor
     * @return team that was generated
     */
    public Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
        return generateTeamForExercise(exercise, name, shortName, "student", numberOfStudents, owner, null, "R");
    }

    /**
     * Generate teams
     *
     * @param exercise        exercise of the teams
     * @param shortNamePrefix prefix that will be added in front of every team's short name
     * @param loginPrefix     prefix that will be added in front of every student's login
     * @param numberOfTeams   amount of teams to generate
     * @param owner           owner of the teams generally a tutor
     * @param creatorLogin    login of user that created the teams
     * @return teams that were generated
     */
    public List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin) {
        return generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, creatorLogin, "R");
    }

    /**
     * Generate teams
     *
     * @param exercise           exercise of the teams
     * @param shortNamePrefix    prefix that will be added in front of every team's short name
     * @param loginPrefix        prefix that will be added in front of every student's login
     * @param numberOfTeams      amount of teams to generate
     * @param owner              owner of the teams generally a tutor
     * @param creatorLogin       login of user that created the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @return teams that were generated
     */
    public List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            int numberOfStudents = new Random().nextInt(4) + 1; // range: 1-4 students
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, numberOfStudents, owner, creatorLogin, registrationPrefix + i));
        }
        return teams;
    }

    /**
     * Generate teams
     *
     * @param exercise           exercise of the teams
     * @param shortNamePrefix    prefix that will be added in front of every team's short name
     * @param loginPrefix        prefix that will be added in front of every student's login
     * @param numberOfTeams      amount of teams to generate
     * @param owner              owner of the teams generally a tutor
     * @param creatorLogin       login of user that created the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @param teamSize           size of each individual team
     * @return teams that were generated
     */
    public List<Team> generateTeamsForExerciseFixedTeamSize(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, teamSize, owner, creatorLogin, registrationPrefix + i));
        }
        return teams;
    }

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner) {
        List<Team> teams = generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, null);
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
        List<Team> teams = generateTeamsForExerciseFixedTeamSize(exercise, userPrefix + "team", "student", numberOfTeams, owner, null, regNumberPrefix, noOfStudentsPerTeam);
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
