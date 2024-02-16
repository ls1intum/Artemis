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
     * Generates a Team for the given Exercise without saving it. Also creates and saves the specified number Users for the Team.
     *
     * @param exercise           The Exercise the Team belongs to
     * @param name               The name of the Team
     * @param shortName          The short name of the Team
     * @param loginPrefix        The prefix that will be added in front of every user's login together with the shortName
     * @param numberOfStudents   The number of students to generate for the Team
     * @param owner              The owner of the Team
     * @param creatorLogin       The login of the user that creates the Teams
     * @param registrationPrefix The prefix that will be added in front of every student's registration number together with the shortName
     * @return The generated Team
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
     * Generates a Team for the given Exercise without saving it. Also creates and saves the specified number Users for the Team.
     *
     * @param exercise         The Exercise the Team belongs to
     * @param name             The name of the Team
     * @param shortName        The short name of the Team
     * @param numberOfStudents The number of students to generate for the Team
     * @param owner            The owner of the Team
     * @return The generated Team
     */
    public Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
        return generateTeamForExercise(exercise, name, shortName, "student", numberOfStudents, owner, null, "R");
    }

    /**
     * Generates a List of Teams with the given arguments without saving the Teams. Also creates and saves between 1 and 4 Users for each Team.
     *
     * @param exercise        The Exercise the Teams belong to
     * @param shortNamePrefix The prefix that will be added in front of every Team's short name
     * @param loginPrefix     The prefix that will be added in front of every user's login together with the shortName
     * @param numberOfTeams   The number of Teams to generate
     * @param owner           The owner of the Teams
     * @param creatorLogin    The login of the user that creates the Teams
     * @return The List of generated Teams
     */
    public List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin) {
        return generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, creatorLogin, "R");
    }

    /**
     * Generates a List of Teams with the given arguments without saving the Teams. Also creates and saves between 1 and 4 Users for each Team.
     *
     * @param exercise           The Exercise the Teams belong to
     * @param shortNamePrefix    The prefix that will be added in front of every Team's short name
     * @param loginPrefix        The prefix that will be added in front of every user's login together with the shortName
     * @param numberOfTeams      The number of Teams to generate
     * @param owner              The owner of the Teams
     * @param creatorLogin       The login of the user that creates the Teams
     * @param registrationPrefix The prefix that will be added in front of every student's registration number together with the shortName
     * @return The List of generated Teams
     */
    public List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            int numberOfStudents = (i % 4) + 1; // range: 1-4 students
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, numberOfStudents, owner, creatorLogin, registrationPrefix + i));
        }
        return teams;
    }

    /**
     * Generates a List of Teams with the given arguments without saving the Teams. Also creates and saves the specified number Users for each Team.
     *
     * @param exercise           The Exercise the Teams belong to
     * @param shortNamePrefix    The prefix that will be added in front of every Team's short name
     * @param loginPrefix        The prefix that will be added in front of every user's login together with the shortName
     * @param numberOfTeams      The number of Teams to generate
     * @param owner              The owner of the Teams
     * @param creatorLogin       The login of the user that creates the Teams
     * @param registrationPrefix The prefix that will be added in front of every student's registration number together with the shortName
     * @param teamSize           The number of students to generate for each Team
     * @return The List of generated Teams
     */
    public List<Team> generateTeamsForExerciseFixedTeamSize(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, teamSize, owner, creatorLogin, registrationPrefix + i));
        }
        return teams;
    }

    /**
     * Creates and saves the given number of Teams. Also creates and saves 1-4 Users for each Team.
     *
     * @param exercise        The Exercise the Teams belong to
     * @param shortNamePrefix The prefix that will be added in front of every Team's short name
     * @param loginPrefix     The prefix that will be added in front of every user's login together with the shortName
     * @param numberOfTeams   The number of Teams to generate
     * @param owner           The owner of the Teams
     * @return The List of created Teams
     */
    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner) {
        List<Team> teams = generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, null);
        var users = teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(userUtilService::cleanUpRegistrationNumberForUser);
        userRepo.saveAll(users);
        return teamRepo.saveAll(teams);
    }

    /**
     * Creates and saves the given number of Teams. Also creates and saves 1-4 Users for each Team.
     *
     * @param exercise        The Exercise the Teams belong to
     * @param shortNamePrefix The prefix that will be added in front of every Team's short name
     * @param numberOfTeams   The number of Teams to generate
     * @param owner           The owner of the Teams
     * @return The List of created Teams
     */
    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, shortNamePrefix, "student", numberOfTeams, owner);
    }

    /**
     * Creates and saves the given number of Teams. Also creates and saves 1-4 Users for each Team.
     *
     * @param exercise      The Exercise the Teams belong to
     * @param numberOfTeams The number of Teams to generate
     * @param owner         The owner of the Teams
     * @return The List of created Teams
     */
    public List<Team> addTeamsForExercise(Exercise exercise, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, "team", numberOfTeams, owner);
    }

    /**
     * Creates and saves the given number of Teams. Also creates and saves the specified number Users for each Team.
     *
     * @param userPrefix          The prefix that will be added in front of every user's login together with the shortName
     * @param regNumberPrefix     The prefix that will be added in front of every student's registration number together with the shortName
     * @param exercise            The Exercise the Teams belong to
     * @param numberOfTeams       The number of Teams to generate
     * @param owner               The owner of the Teams
     * @param noOfStudentsPerTeam The number of students to generate for each Team
     * @return The List of created Teams
     */
    public List<Team> addTeamsForExerciseFixedTeamSize(String userPrefix, String regNumberPrefix, Exercise exercise, int numberOfTeams, User owner, int noOfStudentsPerTeam) {
        List<Team> teams = generateTeamsForExerciseFixedTeamSize(exercise, userPrefix + "team", "student", numberOfTeams, owner, null, regNumberPrefix, noOfStudentsPerTeam);
        var users = teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(userUtilService::cleanUpRegistrationNumberForUser);
        userRepo.saveAll(users);
        return teamRepo.saveAll(teams);
    }

    /**
     * Creates and saves a Team for the given Exercise. Also creates and saves 1 User for the Team.
     *
     * @param exercise The Exercise the Team belongs to
     * @param owner    The owner of the Team
     * @return The created Team
     */
    public Team addTeamForExercise(Exercise exercise, User owner) {
        return addTeamsForExercise(exercise, 1, owner).get(0);
    }

    /**
     * Creates and saves a Team for the given Exercise. Also creates and saves 1 User for the Team.
     *
     * @param exercise    The Exercise the Team belongs to
     * @param owner       The owner of the Team
     * @param loginPrefix The prefix that will be added in front of every user's login together with the shortName
     * @return The created Team
     */
    public Team addTeamForExercise(Exercise exercise, User owner, String loginPrefix) {
        return addTeamsForExercise(exercise, "team", loginPrefix, 1, owner).get(0);
    }

    /**
     * Creates and saves a Team for the given Exercise. Also adds the given Users to the Team.
     *
     * @param students The Users to add to the Team
     * @param owner    The owner of the Team
     * @param exercise The Exercise the Team belongs to
     * @param teamName The name of the Team
     * @return The created Team
     */
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
