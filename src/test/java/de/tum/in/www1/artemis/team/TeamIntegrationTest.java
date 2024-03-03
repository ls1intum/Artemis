package de.tum.in.www1.artemis.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.dto.TeamSearchUserDTO;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CoursesForDashboardDTO;

class TeamIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course;

    private Exercise exercise;

    private Set<User> students;

    private User tutor;

    private static final int NUMBER_OF_STUDENTS = 3;

    private static final long nonExistingId = 123456789L;

    private static final String TEST_PREFIX = "tit";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 2, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        // Make exercise team-based and already released to students
        exercise = course.getExercises().iterator().next();
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise = exerciseRepo.save(exercise);
        students = new HashSet<>(userRepo.searchByLoginOrNameInGroup("tumuser", TEST_PREFIX + "student"));
        tutor = userRepo.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
    }

    private String resourceUrl() {
        return "/api/exercises/" + exercise.getId() + "/teams";
    }

    private String resourceUrlWithWrongExerciseId() {
        return "/api/exercises/" + (exercise.getId() + 1) + "/teams";
    }

    private String resourceUrlExistsTeamByShortName(String shortName) {
        return "/api/courses/" + course.getId() + "/teams/exists?shortName=" + shortName;
    }

    private String resourceUrlSearchUsersInCourse(String loginOrName) {
        return "/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/team-search-users?loginOrName=" + loginOrName;
    }

    private String resourceUrlCourseWithExercisesAndParticipationsForTeam(Course course, Team team) {
        return "/api/courses/" + course.getId() + "/teams/" + team.getShortName() + "/with-exercises-and-participations";
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTeamAssignmentConfig() {
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exercise);
        assertThat(teamAssignmentConfig.getExercise()).isEqualTo(exercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exercise.setTeamAssignmentConfig(teamAssignmentConfig);
        exercise = exerciseRepo.save(exercise);
        exercise = exerciseRepo.findWithEagerCategoriesAndTeamAssignmentConfigById(exercise.getId()).orElseThrow();
        assertThat(exercise.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(1);
        assertThat(exercise.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(10);
        assertThat(exercise.getTeamAssignmentConfig().getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam() throws Exception {
        final String TEAM_NAME = TEST_PREFIX + "Team 1";
        final String TEAM_SHORT_NAME = TEST_PREFIX + "team1";

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

        Team savedTeam = optionalTeam.orElseThrow();
        assertThat(savedTeam.getExercise()).as("Team belongs to correct exercise").isEqualTo(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_StudentsAlreadyAssigned_BadRequest() throws Exception {
        // Create team that contains student "student1"
        Team team1 = new Team().name(TEST_PREFIX + "Team 1").shortName(TEST_PREFIX + "team1").exercise(exercise)
                .students(Set.of(userRepo.findOneByLogin(TEST_PREFIX + "student1").orElseThrow()));
        teamRepo.save(team1);

        // Try to create team with a student that is already assigned to another team
        Team team2 = new Team().name(TEST_PREFIX + "Team 2").shortName(TEST_PREFIX + "team2").exercise(exercise).students(students);
        request.postWithResponseBody(resourceUrl(), team2, Team.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_BadRequest() throws Exception {
        // Try creating a team that already has an id set
        Team team1 = new Team();
        team1.setId(1L);
        request.postWithResponseBody(resourceUrl(), team1, Team.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_Forbidden_AsTutorOfDifferentCourse() throws Exception {
        // If the TA is not part of the correct course TA group anymore, he should not be able to create a team for an exercise of that course
        course.setTeachingAssistantGroupName("Different group name");
        courseRepo.save(course);

        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(exercise);
        team.setStudents(students);
        request.postWithResponseBody(resourceUrl(), team, Team.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_InvalidShortName_BadRequest() throws Exception {
        Team team = new Team();
        team.setName("1 Invalid Name");
        team.setShortName("1InvalidName");
        team.setExercise(exercise);
        team.setStudents(students);
        request.postWithResponseBody(resourceUrl(), team, Team.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam() throws Exception {
        final String TEAM_NAME_UPDATED = TEST_PREFIX + "Team Updated";

        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setName(TEAM_NAME_UPDATED);
        team.setStudents(students);

        Team serverTeam = request.putWithResponseBody(resourceUrl() + "/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getName()).as("Team name was updated correctly").isEqualTo(TEAM_NAME_UPDATED);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").isEqualTo(students);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_BadRequest() throws Exception {
        // Try updating a team that has no id specified
        Team team1 = new Team();
        request.putWithResponseBody(resourceUrl() + "/1", team1, Team.class, HttpStatus.BAD_REQUEST);

        // Try updating a team with an id specified that does not match the team id param in the route
        Team team2 = teamUtilService.addTeamForExercise(exercise, tutor);
        request.putWithResponseBody(resourceUrl() + "/" + (team2.getId() + 1), team2, Team.class, HttpStatus.BAD_REQUEST);

        // Try updating a team with an exercise specified that does not match the exercise id param in the route
        request.putWithResponseBody(resourceUrlWithWrongExerciseId() + "/" + team2.getId(), team2, Team.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_StudentsAlreadyAssigned_BadRequest() throws Exception {
        User student1 = userRepo.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        User student2 = userRepo.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        User student3 = userRepo.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();

        Team team1 = new Team().name(TEST_PREFIX + "Team 1").shortName(TEST_PREFIX + "team1").exercise(exercise).students(Set.of(student1, student2));
        team1.setOwner(tutor);
        teamRepo.save(team1);
        Team team2 = new Team().name(TEST_PREFIX + "Team 2").shortName(TEST_PREFIX + "team2").exercise(exercise).students(Set.of(student3));
        teamRepo.save(team2);

        // Try to update team with a student that is already assigned to another team
        team1.setStudents(students);
        request.putWithResponseBody(resourceUrl() + "/" + team1.getId(), team1, Team.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_NotFound() throws Exception {
        // Try updating a non-existing team
        Team team4 = new Team();
        team4.setId(nonExistingId);
        team4.setExercise(exercise);
        request.putWithResponseBody(resourceUrl() + "/" + team4.getId(), team4, Team.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_Forbidden_AsTutorOfDifferentCourse() throws Exception {
        // If the TA is not part of the correct course TA group anymore, he should not be able to update a team for an exercise of that course
        course.setTeachingAssistantGroupName("Different group name");
        courseRepo.save(course);

        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setName("Updated Team Name");
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), team, Team.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_Forbidden_ShortNameChanged() throws Exception {
        // It should not be allowed to change a team's short name (unique identifier) after creation
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setShortName(TEST_PREFIX + team.getShortName() + " Updated");
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), team, Team.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_Forbidden_OwnerChanged() throws Exception {
        // It should not be allowed to change a team's owner as a tutor
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setOwner(userRepo.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow());
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), team, Team.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeam() throws Exception {
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);

        Team serverTeam = request.get(resourceUrl() + "/" + team.getId(), HttpStatus.OK, Team.class);
        assertThat(serverTeam.getName()).as("Team name was fetched correctly").isEqualTo(team.getName());
        assertThat(serverTeam.getShortName()).as("Team short name was fetched correctly").isEqualTo(team.getShortName());
        assertThat(serverTeam.getStudents()).as("Team students were fetched correctly").isEqualTo(team.getStudents());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeam_BadRequest() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        Exercise wrongExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");

        // Try getting a team with an exercise specified that does not match the exercise id param in the route
        Team team = teamUtilService.addTeamForExercise(wrongExercise, tutor);
        request.get(resourceUrl() + "/" + team.getId(), HttpStatus.BAD_REQUEST, Team.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeam_NotFound() throws Exception {
        request.get(resourceUrl() + "/" + nonExistingId, HttpStatus.NOT_FOUND, Team.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeamsForExercise() throws Exception {
        int numberOfTeams = 3;

        List<Team> teams = teamUtilService.addTeamsForExercise(exercise, numberOfTeams, tutor);
        int numberOfStudents = getCountOfStudentsInTeams(teams);

        List<Team> serverTeams = request.getList(resourceUrl(), HttpStatus.OK, Team.class);
        assertThat(serverTeams).as("Correct number of teams was fetched").hasSize(numberOfTeams);
        assertThat(getCountOfStudentsInTeams(serverTeams)).as("Correct number of students were fetched").isEqualTo(numberOfStudents);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeamsForExercise_Forbidden() throws Exception {
        // If the TA is not part of the correct course TA group anymore, he should not be able to get the teams for an exercise of that course
        course.setTeachingAssistantGroupName("Different group name");
        courseRepo.save(course);
        teamUtilService.addTeamsForExercise(exercise, 3, tutor);
        request.getList(resourceUrl(), HttpStatus.FORBIDDEN, Team.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTeam() throws Exception {
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);

        request.delete(resourceUrl() + "/" + team.getId(), HttpStatus.OK);

        Optional<Team> deletedTeam = teamRepo.findById(team.getId());
        assertThat(deletedTeam).as("Team was deleted correctly").isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteTeam_Forbidden_AsTutor() throws Exception {
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);

        request.delete(resourceUrl() + "/" + team.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTeam_Forbidden_AsInstructorOfDifferentCourse() throws Exception {
        // If the instructor is not part of the correct course instructor group anymore,
        // he should not be able to delete a team for an exercise of that course
        course.setInstructorGroupName("Different group name");
        courseRepo.save(course);

        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        request.delete(resourceUrl() + "/" + team.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTeam_BadRequest() throws Exception {
        // Try deleting a team with an exercise specified that does not match the exercise id param in the route
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        request.delete(resourceUrlWithWrongExerciseId() + "/" + team.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTeam_NotFound() throws Exception {
        request.delete(resourceUrl() + "/" + nonExistingId, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testExistsTeamByShortName() throws Exception {
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);

        final String queryUrl = resourceUrlExistsTeamByShortName(team.getShortName());

        boolean existsOldTeam = request.get(queryUrl, HttpStatus.OK, boolean.class);
        assertThat(existsOldTeam).as("Team with existing short name was correctly found").isTrue();

        boolean existsNewTeam = request.get(queryUrl + "new", HttpStatus.OK, boolean.class);
        assertThat(existsNewTeam).as("Team with new short name was correctly not found").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSearchUsersInCourse() throws Exception {
        // Check that all students from course are found (since their logins are all prefixed by "student")
        List<TeamSearchUserDTO> users1 = request.getList(resourceUrlSearchUsersInCourse(TEST_PREFIX + "student"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users1).as("All users of course with 'student' in login were found").hasSize(NUMBER_OF_STUDENTS);

        // Check that a student is found by his login and that he is NOT marked as "assignedToTeam" yet
        List<TeamSearchUserDTO> users2 = request.getList(resourceUrlSearchUsersInCourse(TEST_PREFIX + "student1"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users2).as("Only user with login " + TEST_PREFIX + "'student1' was found").hasSize(1);
        assertThat(users2.get(0).getAssignedTeamId()).as("User was correctly marked as not being assigned to a team yet").isNull();

        // Check that no student is returned for non-existing login/name
        List<TeamSearchUserDTO> users3 = request.getList(resourceUrlSearchUsersInCourse("chuckNorris"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users3).as("No user was found as expected").isEmpty();

        // Check whether a student from a team is found but marked as "assignedToTeam"
        Team team = teamUtilService.addTeamForExercise(exercise, tutor, TEST_PREFIX);
        User teamStudent = team.getStudents().iterator().next();

        List<TeamSearchUserDTO> users4 = request.getList(resourceUrlSearchUsersInCourse(teamStudent.getLogin()), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users4).as("User from team was found").hasSize(1);
        assertThat(users4.get(0).getAssignedTeamId()).as("User from team was correctly marked as being assigned to a team already").isEqualTo(team.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSearchUsersInCourse_BadRequest() throws Exception {
        // Search terms that are shorter than 3 characters should lead to bad request
        request.getList(resourceUrlSearchUsersInCourse("ab"), HttpStatus.BAD_REQUEST, TeamSearchUserDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSearchUsersInCourse_Forbidden_AsTutorOfDifferentCourse() throws Exception {
        // If the TA is not part of the correct course TA group anymore, he should not be able to search for users in the course
        course.setTeachingAssistantGroupName("Different group name");
        courseRepo.save(course);

        request.getList(resourceUrlSearchUsersInCourse(TEST_PREFIX + "student"), HttpStatus.FORBIDDEN, TeamSearchUserDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTeamOperationsAsStudent() throws Exception {
        Team existingTeam = teamUtilService.addTeamForExercise(exercise, tutor);
        Team unsavedTeam = teamUtilService.generateTeamForExercise(exercise, TEST_PREFIX + "Team Unsaved", TEST_PREFIX + "teamUnsaved", 2, tutor);

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
        request.getList(resourceUrlSearchUsersInCourse(TEST_PREFIX + "student"), HttpStatus.FORBIDDEN, TeamSearchUserDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAssignedTeamIdOnExerciseForCurrentUser() throws Exception {
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setGroups(Set.of(TEST_PREFIX + "student" + "assignedTeam"));
        userRepo.save(student);

        course.setStudentGroupName(TEST_PREFIX + "student" + "assignedTeam");
        courseRepo.save(course);

        // Create team that contains student "student1" (Team shortName needs to be empty since it is used as a prefix for the generated student logins)
        Team team = new Team().name(TEST_PREFIX + "Team").shortName(TEST_PREFIX + "team").exercise(exercise)
                .students(userRepo.findOneByLogin(TEST_PREFIX + "student1").map(Set::of).orElseThrow());
        team = teamRepo.save(team);

        // Check for endpoint: @GetMapping("courses/for-dashboard")
        var courses = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        Exercise serverExercise = courses.courses().stream().filter(c -> c.course().getId().equals(course.getId())).findAny()
                .flatMap(c -> c.course().getExercises().stream().filter(e -> e.getId().equals(exercise.getId())).findAny()).orElseThrow();
        assertThat(serverExercise.getStudentAssignedTeamId()).as("Assigned team id on exercise from dashboard is correct for student.").isEqualTo(team.getId());
        assertThat(serverExercise.isStudentAssignedTeamIdComputed()).as("Assigned team id on exercise was computed.").isTrue();

        // Check for endpoint: @GetMapping("exercises/{exerciseId}/details")
        Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
        assertThat(exerciseWithDetails.getStudentAssignedTeamId()).as("Assigned team id on exercise from details is correct for student.").isEqualTo(team.getId());
        assertThat(serverExercise.isStudentAssignedTeamIdComputed()).as("Assigned team id on exercise was computed.").isTrue();
    }

    /**
     * Sums up the number of students in a list of teams
     *
     * @param teams Teams for which to count all students
     * @return count of students in all teams
     */
    private int getCountOfStudentsInTeams(List<Team> teams) {
        return teams.stream().map(Team::getStudents).map(Set::size).reduce(0, Integer::sum);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getCourseWithExercisesAndParticipationsForTeam_AsTutor() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, 5);
        Course course = courses.get(0);

        ProgrammingExercise programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        TextExercise textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        ModelingExercise modelingExercise = exerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);

        // make exercises team-based
        Stream.of(programmingExercise, textExercise, modelingExercise).forEach(exercise -> {
            exercise.setMode(ExerciseMode.TEAM);
            exerciseRepo.save(exercise);
        });

        String shortNamePrefix1 = TEST_PREFIX + "team";
        String shortNamePrefix2 = TEST_PREFIX + "otherTeam";

        Team team1a = teamUtilService.addTeamsForExercise(programmingExercise, shortNamePrefix1, TEST_PREFIX + "team1astudent", 1, tutor).get(0);
        Team team1b = teamUtilService.addTeamsForExercise(textExercise, shortNamePrefix1, TEST_PREFIX + "team1bstudent", 1, tutor).get(0);
        Team team1c = teamUtilService.addTeamsForExercise(modelingExercise, shortNamePrefix1, TEST_PREFIX + "team1cstudent", 1, tutor).get(0);

        Team team2a = teamUtilService.addTeamsForExercise(programmingExercise, shortNamePrefix2, TEST_PREFIX + "team2astudent", 1, null).get(0);
        Team team2b = teamUtilService.addTeamsForExercise(textExercise, shortNamePrefix2, TEST_PREFIX + "team2bstudent", 1, null).get(0);

        assertThat(Stream.of(team1a, team1b, team1c).map(Team::getShortName).distinct()).as("Teams 1 need the same short name for this test").hasSize(1);
        assertThat(Stream.of(team2a, team2b).map(Team::getShortName).distinct()).as("Teams 2 need the same short name for this test").hasSize(1);
        assertThat(Stream.of(team1a, team1b, team1c, team2a, team2b).map(Team::getShortName).distinct()).as("Teams 1 and Teams 2 need different short names").hasSize(2);

        participationUtilService.addTeamParticipationForExercise(programmingExercise, team1a.getId());
        participationUtilService.addTeamParticipationForExercise(textExercise, team1b.getId());

        participationUtilService.addTeamParticipationForExercise(programmingExercise, team2a.getId());
        participationUtilService.addTeamParticipationForExercise(textExercise, team2b.getId());

        Course course1 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team1a), HttpStatus.OK, Course.class);
        assertThat(course1.getExercises()).as("All exercises of team 1 in course were returned").hasSize(3);
        assertThat(course1.getExercises().stream().map(Exercise::getTeams).collect(Collectors.toSet())).as("All team instances of team 1 in course were returned").hasSize(3);
        assertThat(course1.getExercises().stream().flatMap(exercise -> exercise.getStudentParticipations().stream()).collect(Collectors.toSet()))
                .as("All participations of team 1 in course were returned").hasSize(2);

        Course course2 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team2a), HttpStatus.OK, Course.class);
        assertThat(course2.getExercises()).as("All exercises of team 2 in course were returned").hasSize(2);

        StudentParticipation studentParticipation = course2.getExercises().iterator().next().getStudentParticipations().iterator().next();
        assertThat(studentParticipation.getSubmissionCount()).as("Participation includes submission count").isNotNull();

        // Submission and Result should be present for Team of which the user is the Team Owner
        final String submissionText = "Hello World";
        TextSubmission submission = ParticipationFactory.generateTextSubmission(submissionText, Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, submission, team1b.getId(), tutor.getLogin());

        Course course3 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team1a), HttpStatus.OK, Course.class);
        StudentParticipation participation = course3.getExercises().stream().filter(exercise -> exercise.equals(textExercise)).findAny().orElseThrow().getStudentParticipations()
                .iterator().next();
        assertThat(participation.getSubmissions()).as("Latest submission is present").hasSize(1);
        assertThat(((TextSubmission) participation.getSubmissions().iterator().next()).getText()).as("Latest submission is present").isEqualTo(submissionText);
        assertThat(participation.getResults()).as("Latest result is present").hasSize(1);

        // Submission and Result should not be present for a Team of which the user is not (!) the Team Owner
        submission = ParticipationFactory.generateTextSubmission(submissionText, Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, submission, team2b.getId(), TEST_PREFIX + "tutor2");

        Course course4 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team2a), HttpStatus.OK, Course.class);
        participation = course4.getExercises().stream().filter(exercise -> exercise.equals(textExercise)).findAny().orElseThrow().getStudentParticipations().iterator().next();
        assertThat(participation.getSubmissions()).as("Latest submission is not present").isEmpty();
        assertThat(participation.getResults()).as("Latest result is not present").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseWithExercisesAndParticipationsForTeam_AsStudentInTeam_Allowed() throws Exception {
        Team team = teamRepo.save(new Team().name(TEST_PREFIX + "Team").shortName(TEST_PREFIX + "team").exercise(exercise)
                .students(userRepo.findOneByLogin(TEST_PREFIX + "student1").map(Set::of).orElseThrow()));
        request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team), HttpStatus.OK, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseWithExercisesAndParticipationsForTeam_AsStudentNotInTeam_Forbidden() throws Exception {
        Team team = teamUtilService.addTeamsForExercise(exercise, TEST_PREFIX + "team_forb", TEST_PREFIX + "otherStudent", 1, tutor).get(0);
        request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team), HttpStatus.FORBIDDEN, Course.class);
    }
}
