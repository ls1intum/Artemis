package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.dto.TeamSearchUserDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class TeamIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    TeamRepository teamRepo;

    @Autowired
    UserRepository userRepo;

    private Course course;

    private Exercise exercise;

    private Set<User> students;

    private final static int numberOfStudentsInCourse = 3;

    private String resourceUrl() {
        return "/api/exercises/" + exercise.getId() + "/teams";
    }

    private String resourceUrlExistsTeamByShortName(String shortName) {
        return "/api/teams?shortName=" + shortName;
    }

    private String resourceUrlSearchUsersInCourse(String loginOrName) {
        return "/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/team-search-users?loginOrName=" + loginOrName;
    }

    @BeforeEach
    public void initTestCase() {
        database.addUsers(numberOfStudentsInCourse, 1, 1);
        database.addCourseWithOneProgrammingExercise();
        course = courseRepo.findAll().get(0);
        exercise = exerciseRepo.findAll().get(0);
        students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateTeam() throws Exception {
        final String TEAM_NAME = "Team 1";
        final String TEAM_SHORT_NAME = "team1";

        Team team = new Team();
        team.setName(TEAM_NAME);
        team.setShortName(TEAM_SHORT_NAME);
        team.setExercise(exercise);
        team.setStudents(students);

        Team serverTeam = request.postWithResponseBody(resourceUrl(), team, Team.class, HttpStatus.CREATED);

        assertThat(serverTeam.getName()).as("Team has correct name").isEqualTo(TEAM_NAME);
        assertThat(serverTeam.getShortName()).as("Team has correct short name").isEqualTo(TEAM_SHORT_NAME);
        assertThat(serverTeam.getStudents()).as("Team has correct students assigned").isEqualTo(students);

        Optional<Team> optionalTeam = teamRepo.findById(serverTeam.getId());
        assertThat(optionalTeam).as("Team was saved to database").isPresent();

        Team savedTeam = optionalTeam.get();
        assertThat(savedTeam.getExercise()).as("Team belongs to correct exercise").isEqualTo(exercise);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateTeam() throws Exception {
        final String TEAM_NAME_UPDATED = "Team Updated";

        Team team = database.addTeamForExercise(exercise);
        team.setName(TEAM_NAME_UPDATED);
        team.setStudents(students);

        Team serverTeam = request.putWithResponseBody(resourceUrl() + "/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getName()).as("Team name was updated correctly").isEqualTo(TEAM_NAME_UPDATED);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").isEqualTo(students);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateTeamShortNameForbidden() throws Exception {
        Team team = database.addTeamForExercise(exercise);
        team.setShortName(team.getShortName() + " Updated");
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), team, Team.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetTeam() throws Exception {
        Team team = database.addTeamForExercise(exercise);

        Team serverTeam = request.get(resourceUrl() + "/" + team.getId(), HttpStatus.OK, Team.class);
        assertThat(serverTeam.getName()).as("Team name was fetched correctly").isEqualTo(team.getName());
        assertThat(serverTeam.getShortName()).as("Team short name was fetched correctly").isEqualTo(team.getShortName());
        assertThat(serverTeam.getStudents()).as("Team students were fetched correctly").isEqualTo(team.getStudents());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetTeamsForExercise() throws Exception {
        int numberOfTeams = 3;

        List<Team> teams = database.addTeamsForExercise(exercise, numberOfTeams);
        int numberOfStudents = getCountOfStudentsInTeams(teams);

        List<Team> serverTeams = request.getList(resourceUrl(), HttpStatus.OK, Team.class);
        assertThat(serverTeams).as("Correct number of teams was fetched").hasSize(numberOfTeams);
        assertThat(getCountOfStudentsInTeams(serverTeams)).as("Correct number of students were fetched").isEqualTo(numberOfStudents);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteTeam() throws Exception {
        Team team = database.addTeamForExercise(exercise);

        request.delete(resourceUrl() + "/" + team.getId(), HttpStatus.OK);

        Optional<Team> deletedTeam = teamRepo.findById(team.getId());
        assertThat(deletedTeam).as("Team was deleted correctly").isNotPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testExistsTeamByShortName() throws Exception {
        Team team = database.addTeamForExercise(exercise);

        final String queryUrl = resourceUrlExistsTeamByShortName(team.getShortName());

        boolean existsOldTeam = request.get(queryUrl, HttpStatus.OK, boolean.class);
        assertThat(existsOldTeam).as("Team with existing short name was correctly found").isTrue();

        boolean existsNewTeam = request.get(queryUrl + "new", HttpStatus.OK, boolean.class);
        assertThat(existsNewTeam).as("Team with new short name was correctly not found").isFalse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSearchUsersInCourse() throws Exception {
        // Check that all students from course are found (since their logins are all prefixed by "student")
        List<TeamSearchUserDTO> users1 = request.getList(resourceUrlSearchUsersInCourse("student"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users1).as("All users of course with 'student' in login were found").hasSize(numberOfStudentsInCourse);

        // Check that a student is found by his login and that he is NOT marked as "isAssignedToTeam" yet
        List<TeamSearchUserDTO> users2 = request.getList(resourceUrlSearchUsersInCourse("student1"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users2).as("Only user with login 'student1' was found").hasSize(1);
        assertThat(users2.get(0).isAssignedToTeam()).as("User was correctly marked as not being assigned to a team yet").isFalse();

        // Check that no student is returned for non-existing login/name
        List<TeamSearchUserDTO> users3 = request.getList(resourceUrlSearchUsersInCourse("chuckNorris"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users3).as("No user was found as expected").isEmpty();

        // Check whether a student from a team is found but marked as "isAssignedToTeam"
        Team team = database.addTeamForExercise(exercise);
        User teamStudent = team.getStudents().iterator().next();

        List<TeamSearchUserDTO> users4 = request.getList(resourceUrlSearchUsersInCourse(teamStudent.getLogin()), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users4).as("User from team was found").hasSize(1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testTeamOperationsAsStudent() throws Exception {
        Team existingTeam = database.addTeamForExercise(exercise);
        Team unsavedTeam = ModelFactory.generateTeamForExercise(exercise, "Team Unsaved", "teamUnsaved", 2);

        // Create team
        request.postWithResponseBody(resourceUrl(), unsavedTeam, Team.class, HttpStatus.FORBIDDEN);
        // Update team
        request.putWithResponseBody(resourceUrl() + "/" + existingTeam.getId(), existingTeam, Team.class, HttpStatus.FORBIDDEN);
        // Get other team
        request.get(resourceUrl() + "/" + existingTeam.getId(), HttpStatus.FORBIDDEN, Team.class);
        // Get all teams for exercise
        request.getList(resourceUrl(), HttpStatus.FORBIDDEN, Team.class);
        // Delete team
        request.delete(resourceUrl() + "/" + existingTeam.getId(), HttpStatus.FORBIDDEN);
        // Exists team by shortName
        request.get(resourceUrlExistsTeamByShortName(existingTeam.getShortName()), HttpStatus.FORBIDDEN, boolean.class);
        // Search users in course
        request.getList(resourceUrlSearchUsersInCourse("student"), HttpStatus.FORBIDDEN, TeamSearchUserDTO.class);
    }

    /**
     * Sums up the number of students in a list of teams
     * @param teams Teams for which to count all students
     * @return count of students in all teams
     */
    private int getCountOfStudentsInTeams(List<Team> teams) {
        return teams.stream().map(Team::getStudents).map(Set::size).reduce(0, Integer::sum);
    }
}
