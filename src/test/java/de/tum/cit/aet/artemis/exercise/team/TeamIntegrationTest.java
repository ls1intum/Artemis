package de.tum.cit.aet.artemis.exercise.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CoursesForDashboardDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.dto.CourseWithTeamExercisesDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamMemberDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamResponseDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamSearchUserDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TeamIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course;

    private Exercise exercise;

    private Set<User> students;

    private User tutor;

    private static final int NUMBER_OF_STUDENTS = 3;

    private static final long NON_EXISTING_ID = 123456789L;

    private static final String TEST_PREFIX = "tit";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 2, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);

        // Make exercise team-based and already released to students
        exercise = course.getExercises().iterator().next();
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise = exerciseRepository.save(exercise);
        students = new HashSet<>(userTestRepository.findAllByUserPrefix(TEST_PREFIX + "student"));
        tutor = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
    }

    private String resourceUrl() {
        return "/api/exercise/exercises/" + exercise.getId() + "/teams";
    }

    private String resourceUrlWithWrongExerciseId() {
        return "/api/exercise/exercises/" + (exercise.getId() + 1) + "/teams";
    }

    private String resourceUrlExistsTeamByShortName(String shortName) {
        return "/api/exercise/courses/" + course.getId() + "/teams/exists?shortName=" + shortName;
    }

    private String resourceUrlSearchUsersInCourse(String loginOrName) {
        return "/api/exercise/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/team-search-users?loginOrName=" + loginOrName;
    }

    private String resourceUrlCourseWithExercisesAndParticipationsForTeam(Course course, Team team) {
        return "/api/exercise/courses/" + course.getId() + "/teams/" + team.getShortName() + "/with-exercises-and-participations";
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
        exercise = exerciseRepository.save(exercise);
        exercise = exerciseRepository.findWithEagerCategoriesAndTeamAssignmentConfigById(exercise.getId()).orElseThrow();
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

        TeamResponseDTO serverTeam = request.postWithResponseBody(resourceUrl(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.CREATED);

        assertThat(serverTeam.name()).as("Team has correct name").isEqualTo(TEAM_NAME);
        assertThat(serverTeam.shortName()).as("Team has correct short name").isEqualTo(TEAM_SHORT_NAME);
        assertThat(loginsOf(serverTeam)).as("Team has correct students assigned").containsExactlyInAnyOrderElementsOf(loginsOf(students));
        assertThat(serverTeam.createdDate()).as("Team reports when it was created").isNotNull();

        Optional<Team> optionalTeam = teamRepo.findById(serverTeam.id());
        assertThat(optionalTeam).as("Team was saved to database").isPresent();

        Team savedTeam = optionalTeam.orElseThrow();
        assertThat(savedTeam.getExercise()).as("Team belongs to correct exercise").isEqualTo(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_StudentsAlreadyAssigned_BadRequest() throws Exception {
        // Create team that contains student "student1"
        Team team1 = new Team().name(TEST_PREFIX + "Team 1").shortName(TEST_PREFIX + "team1").exercise(exercise)
                .students(Set.of(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow()));
        teamRepo.save(team1);

        // Try to create team with a student that is already assigned to another team
        Team team2 = new Team().name(TEST_PREFIX + "Team 2").shortName(TEST_PREFIX + "team2").exercise(exercise).students(students);
        request.postWithResponseBody(resourceUrl(), TeamInputDTO.of(team2), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_BadRequest() throws Exception {
        // Try creating a team that already has an id set
        Team team1 = new Team();
        team1.setId(1L);
        team1.setName("team");
        team1.setShortName("team");
        request.postWithResponseBody(resourceUrl(), TeamInputDTO.of(team1), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_Forbidden_AsTutorOfDifferentCourse() throws Exception {
        // Revoke the tutor's UCR entry so they no longer have TA access to this course
        userUtilService.unenrollUserFromCourseByRole(tutor, course, CourseRole.TEACHING_ASSISTANT);

        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(exercise);
        team.setStudents(students);
        request.postWithResponseBody(resourceUrl(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTeam_InvalidShortName_BadRequest() throws Exception {
        Team team = new Team();
        team.setName("1 Invalid Name");
        team.setShortName("1invalid");
        team.setExercise(exercise);
        team.setStudents(students);
        request.postWithResponseBody(resourceUrl(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam() throws Exception {
        final String TEAM_NAME_UPDATED = TEST_PREFIX + "Team Updated";

        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setName(TEAM_NAME_UPDATED);
        team.setStudents(students);

        TeamResponseDTO serverTeam = request.putWithResponseBody(resourceUrl() + "/" + team.getId(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.OK);
        assertThat(serverTeam.name()).as("Team name was updated correctly").isEqualTo(TEAM_NAME_UPDATED);
        assertThat(loginsOf(serverTeam)).as("Team students were updated correctly").containsExactlyInAnyOrderElementsOf(loginsOf(students));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_BadRequest() throws Exception {
        // Try updating a team that has no id specified
        var dto1 = new TeamInputDTO(null, "name", "shortname", null, null, null);
        request.putWithResponseBody(resourceUrl() + "/1", dto1, TeamResponseDTO.class, HttpStatus.BAD_REQUEST);

        // Try updating a team with an id specified that does not match the team id param in the route
        Team team2 = teamUtilService.addTeamForExercise(exercise, tutor);
        request.putWithResponseBody(resourceUrl() + "/" + (team2.getId() + 1), TeamInputDTO.of(team2), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);

        // Try updating a team with an exercise specified that does not match the exercise id param in the route
        request.putWithResponseBody(resourceUrlWithWrongExerciseId() + "/" + team2.getId(), TeamInputDTO.of(team2), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_StudentsAlreadyAssigned_BadRequest() throws Exception {
        User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        User student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        User student3 = userTestRepository.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();

        Team team1 = new Team().name(TEST_PREFIX + "Team 1").shortName(TEST_PREFIX + "team1").exercise(exercise).students(Set.of(student1, student2));
        team1.setOwner(tutor);
        teamRepo.save(team1);
        Team team2 = new Team().name(TEST_PREFIX + "Team 2").shortName(TEST_PREFIX + "team2").exercise(exercise).students(Set.of(student3));
        teamRepo.save(team2);

        // Try to update team with a student that is already assigned to another team
        team1.setStudents(students);
        request.putWithResponseBody(resourceUrl() + "/" + team1.getId(), TeamInputDTO.of(team1), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_NotFound() throws Exception {
        // Try updating a non-existing team
        var dto = new TeamInputDTO(NON_EXISTING_ID, "name", "shortname", null, null, null);
        request.putWithResponseBody(resourceUrl() + "/" + NON_EXISTING_ID, dto, TeamResponseDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_Forbidden_AsTutorOfDifferentCourse() throws Exception {
        // Revoke the tutor's UCR entry so they no longer have TA access to this course
        userUtilService.unenrollUserFromCourseByRole(tutor, course, CourseRole.TEACHING_ASSISTANT);

        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setName("Updated Team Name");
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_Forbidden_ShortNameChanged() throws Exception {
        // It should not be allowed to change a team's short name (unique identifier) after creation
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setShortName("changed");
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateTeam_Forbidden_OwnerChanged() throws Exception {
        // It should not be allowed to change a team's owner as a tutor
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);
        team.setOwner(userTestRepository.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow());
        request.putWithResponseBody(resourceUrl() + "/" + team.getId(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeam() throws Exception {
        Team team = teamUtilService.addTeamForExercise(exercise, tutor);

        TeamResponseDTO serverTeam = request.get(resourceUrl() + "/" + team.getId(), HttpStatus.OK, TeamResponseDTO.class);
        assertThat(serverTeam.name()).as("Team name was fetched correctly").isEqualTo(team.getName());
        assertThat(serverTeam.shortName()).as("Team short name was fetched correctly").isEqualTo(team.getShortName());
        assertThat(loginsOf(serverTeam)).as("Team students were fetched correctly").containsExactlyInAnyOrderElementsOf(loginsOf(team.getStudents()));
        assertThat(serverTeam.owner()).as("Team owner was fetched correctly").isNotNull();
        assertThat(serverTeam.owner().login()).as("The team list filters and the detail header read the owner login").isEqualTo(tutor.getLogin());
        assertThat(serverTeam.owner().name()).as("The team list sorts on the owner name").isEqualTo(tutor.getName());
        assertThat(serverTeam.owner().email()).as("The detail header links the owner email").isEqualTo(tutor.getEmail());
        assertThat(serverTeam.createdBy()).as("The detail header links the creator").isNotNull().isEqualTo(team.getCreatedBy());
        assertThat(serverTeam.createdDate()).as("The detail header shows when the team was created").isNotNull();
        assertThat(serverTeam.lastModifiedBy()).as("The detail header links the last editor").isNotNull().isEqualTo(team.getLastModifiedBy());
        assertThat(serverTeam.lastModifiedDate()).as("The detail header shows when the team was last changed").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeam_BadRequest() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        Exercise wrongExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");

        // Try getting a team with an exercise specified that does not match the exercise id param in the route
        Team team = teamUtilService.addTeamForExercise(wrongExercise, tutor);
        request.get(resourceUrl() + "/" + team.getId(), HttpStatus.BAD_REQUEST, TeamResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeam_NotFound() throws Exception {
        request.get(resourceUrl() + "/" + NON_EXISTING_ID, HttpStatus.NOT_FOUND, TeamResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeamsForExercise() throws Exception {
        int numberOfTeams = 3;

        List<Team> teams = teamUtilService.addTeamsForExercise(exercise, numberOfTeams, tutor);
        int numberOfStudents = getCountOfStudentsInTeams(teams);

        List<TeamResponseDTO> serverTeams = request.getList(resourceUrl(), HttpStatus.OK, TeamResponseDTO.class);
        assertThat(serverTeams).as("Correct number of teams was fetched").hasSize(numberOfTeams);
        assertThat(serverTeams.stream().mapToInt(serverTeam -> serverTeam.students().size()).sum()).as("Correct number of students were fetched").isEqualTo(numberOfStudents);
        assertThat(serverTeams).as("Every student carries the registration number the exercise administration shows")
                .allSatisfy(serverTeam -> assertThat(serverTeam.students()).allSatisfy(student -> assertThat(student.visibleRegistrationNumber()).isNotNull()));
        assertThat(serverTeams).as("Every team carries the owner the team list sorts and filters on").allSatisfy(serverTeam -> {
            assertThat(serverTeam.owner()).isNotNull();
            assertThat(serverTeam.owner().login()).isEqualTo(tutor.getLogin());
            assertThat(serverTeam.owner().name()).isEqualTo(tutor.getName());
            assertThat(serverTeam.owner().email()).isEqualTo(tutor.getEmail());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTeamsForExercise_Forbidden() throws Exception {
        // Revoke the tutor's UCR entry so they no longer have TA access to this course
        userUtilService.unenrollUserFromCourseByRole(tutor, course, CourseRole.TEACHING_ASSISTANT);
        teamUtilService.addTeamsForExercise(exercise, 3, tutor);
        request.getList(resourceUrl(), HttpStatus.FORBIDDEN, TeamResponseDTO.class);
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
    void testDeleteTeam_Forbidden_WhenNotInstructorInCourse() throws Exception {
        // Revoke the instructor's UCR entry so they no longer have access to this course
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.unenrollUserFromCourseByRole(instructor, course, CourseRole.INSTRUCTOR);

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
        request.delete(resourceUrl() + "/" + NON_EXISTING_ID, HttpStatus.NOT_FOUND);
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

        // Check that a student is found by their login and that they are NOT marked as "assignedToTeam" yet
        List<TeamSearchUserDTO> users2 = request.getList(resourceUrlSearchUsersInCourse(TEST_PREFIX + "student1"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users2).as("Only user with login " + TEST_PREFIX + "'student1' was found").hasSize(1);
        assertThat(users2.getFirst().assignedTeamId()).as("User was correctly marked as not being assigned to a team yet").isNull();

        // Check that no student is returned for non-existing login/name
        List<TeamSearchUserDTO> users3 = request.getList(resourceUrlSearchUsersInCourse("chuckNorris"), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users3).as("No user was found as expected").isEmpty();

        // Check whether a student from a team is found but marked as "assignedToTeam"
        Team team = teamUtilService.addTeamForExercise(exercise, tutor, TEST_PREFIX);
        team.getStudents().forEach(s -> userUtilService.enrollUserInCourse(s, course, CourseRole.STUDENT));
        User teamStudent = team.getStudents().iterator().next();

        List<TeamSearchUserDTO> users4 = request.getList(resourceUrlSearchUsersInCourse(teamStudent.getLogin()), HttpStatus.OK, TeamSearchUserDTO.class);
        assertThat(users4).as("User from team was found").hasSize(1);
        assertThat(users4.getFirst().assignedTeamId()).as("User from team was correctly marked as being assigned to a team already").isEqualTo(team.getId());
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
        // Revoke the tutor's UCR entry so they no longer have TA access to this course
        userUtilService.unenrollUserFromCourseByRole(tutor, course, CourseRole.TEACHING_ASSISTANT);

        request.getList(resourceUrlSearchUsersInCourse(TEST_PREFIX + "student"), HttpStatus.FORBIDDEN, TeamSearchUserDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTeamOperationsAsStudent() throws Exception {
        Team existingTeam = teamUtilService.addTeamForExercise(exercise, tutor);
        Team unsavedTeam = teamUtilService.generateTeamForExercise(exercise, "Team Unsaved", "unsaved", 2, tutor);

        // Create team
        request.postWithResponseBody(resourceUrl(), TeamInputDTO.of(unsavedTeam), TeamResponseDTO.class, HttpStatus.FORBIDDEN);
        // Update team
        request.putWithResponseBody(resourceUrl() + "/" + existingTeam.getId(), TeamInputDTO.of(existingTeam), TeamResponseDTO.class, HttpStatus.FORBIDDEN);
        // Get other team
        request.get(resourceUrl() + "/" + existingTeam.getId(), HttpStatus.FORBIDDEN, TeamResponseDTO.class);
        // Get all teams for exercise
        request.getList(resourceUrl(), HttpStatus.FORBIDDEN, TeamResponseDTO.class);
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
        // student1 is already enrolled as STUDENT via prefix enrollment in initTestCase
        // Create team that contains student "student1" (Team shortName needs to be empty since it is used as a prefix for the generated student logins)
        Team team = new Team().name(TEST_PREFIX + "Team").shortName(TEST_PREFIX + "team").exercise(exercise)
                .students(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").map(Set::of).orElseThrow());
        team = teamRepo.save(team);

        // Check for endpoint: @GetMapping("courses/for-dashboard")
        var courses = request.get("/api/course/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
        Exercise serverExercise = courses.courses().stream().filter(c -> c.course().getId().equals(course.getId())).findAny()
                .flatMap(c -> c.course().getExercises().stream().filter(e -> e.getId().equals(exercise.getId())).findAny()).orElseThrow();
        assertThat(serverExercise.getStudentAssignedTeamId()).as("Assigned team id on exercise from dashboard is correct for student.").isEqualTo(team.getId());
        assertThat(serverExercise.isStudentAssignedTeamIdComputed()).as("Assigned team id on exercise was computed.").isTrue();

        // Check for endpoint: @GetMapping("exercises/{exerciseId}/details")
        ExerciseDetailsDTO exerciseWithDetails = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
        assertThat(exerciseWithDetails.exercise().getStudentAssignedTeamId()).as("Assigned team id on exercise from details is correct for student.").isEqualTo(team.getId());
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

    /**
     * The participations of an exercise. An exercise the team has no participation in omits the empty list on the wire,
     * which the client reads as "no participation" and this reads as an empty list.
     *
     * @param exercise the exercise as the response reports it
     * @return its participations, empty when it has none
     */
    private static List<TeamParticipationDTO> participationsOf(CourseWithTeamExercisesDTO.TeamExerciseDTO exercise) {
        return exercise.studentParticipations() == null ? List.of() : exercise.studentParticipations();
    }

    private static Set<String> loginsOf(TeamResponseDTO team) {
        return team.students().stream().map(TeamMemberDTO::login).collect(Collectors.toSet());
    }

    private static Set<String> loginsOf(Set<User> users) {
        return users.stream().map(User::getLogin).collect(Collectors.toSet());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getCourseWithExercisesAndParticipationsForTeam_AsTutor() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, false, 5);
        Course course = courses.getFirst();

        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        ModelingExercise modelingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);

        // make exercises team-based
        Stream.of(programmingExercise, textExercise, modelingExercise).forEach(exercise -> {
            exercise.setMode(ExerciseMode.TEAM);
            exerciseRepository.save(exercise);
        });

        String shortNamePrefix1 = TEST_PREFIX + "team";
        String shortNamePrefix2 = TEST_PREFIX + "otherTeam";

        Team team1a = teamUtilService.addTeamsForExercise(programmingExercise, shortNamePrefix1, TEST_PREFIX + "team1astudent", 1, tutor).getFirst();
        Team team1b = teamUtilService.addTeamsForExercise(textExercise, shortNamePrefix1, TEST_PREFIX + "team1bstudent", 1, tutor).getFirst();
        Team team1c = teamUtilService.addTeamsForExercise(modelingExercise, shortNamePrefix1, TEST_PREFIX + "team1cstudent", 1, tutor).getFirst();

        Team team2a = teamUtilService.addTeamsForExercise(programmingExercise, shortNamePrefix2, TEST_PREFIX + "team2astudent", 1, null).getFirst();
        Team team2b = teamUtilService.addTeamsForExercise(textExercise, shortNamePrefix2, TEST_PREFIX + "team2bstudent", 1, null).getFirst();

        assertThat(Stream.of(team1a, team1b, team1c).map(Team::getShortName).distinct()).as("Teams 1 need the same short name for this test").hasSize(1);
        assertThat(Stream.of(team2a, team2b).map(Team::getShortName).distinct()).as("Teams 2 need the same short name for this test").hasSize(1);
        assertThat(Stream.of(team1a, team1b, team1c, team2a, team2b).map(Team::getShortName).distinct()).as("Teams 1 and Teams 2 need different short names").hasSize(2);

        participationUtilService.addTeamParticipationForExercise(programmingExercise, team1a.getId());
        participationUtilService.addTeamParticipationForExercise(textExercise, team1b.getId());

        participationUtilService.addTeamParticipationForExercise(programmingExercise, team2a.getId());
        participationUtilService.addTeamParticipationForExercise(textExercise, team2b.getId());

        CourseWithTeamExercisesDTO course1 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team1a), HttpStatus.OK, CourseWithTeamExercisesDTO.class);
        assertThat(course1.id()).as("The course the team belongs to was returned").isEqualTo(course.getId());
        assertThat(course1.exercises()).as("All exercises of team 1 in course were returned").hasSize(3);
        assertThat(course1.exercises()).as("Every exercise carries the team instance the client links to").allSatisfy(exercise -> assertThat(exercise.teams()).hasSize(1));
        assertThat(course1.exercises()).as("Every exercise carries the title and dates the participation table renders").allSatisfy(teamExercise -> {
            assertThat(teamExercise.title()).isNotNull();
            assertThat(teamExercise.releaseDate()).isNotNull();
            assertThat(teamExercise.dueDate()).isNotNull();
        });
        assertThat(course1.exercises().stream().flatMap(exercise -> participationsOf(exercise).stream()).toList()).as("All participations of team 1 in course were returned")
                .hasSize(2);

        CourseWithTeamExercisesDTO course2 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team2a), HttpStatus.OK, CourseWithTeamExercisesDTO.class);
        assertThat(course2.exercises()).as("All exercises of team 2 in course were returned").hasSize(2);

        TeamParticipationDTO studentParticipation = course2.exercises().stream().flatMap(exercise -> participationsOf(exercise).stream()).findFirst().orElseThrow();
        assertThat(studentParticipation.submissionCount()).as("Participation includes submission count").isNotNull();
        assertThat(studentParticipation.type()).as("Participation reports its kind so the client can merge it").isEqualTo("student");

        // Submission and Result should be present for Team of which the user is the Team Owner
        final String submissionText = "Hello World";
        TextSubmission submission = ParticipationFactory.generateTextSubmission(submissionText, Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, submission, team1b.getId(), tutor.getLogin());

        CourseWithTeamExercisesDTO course3 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team1a), HttpStatus.OK, CourseWithTeamExercisesDTO.class);
        TeamParticipationDTO participation = participationsOf(course3.exercises().stream().filter(exercise -> exercise.id().equals(textExercise.getId())).findAny().orElseThrow())
                .getFirst();
        assertThat(participation.submissions()).as("Latest submission is present").hasSize(1);
        var returnedSubmission = participation.submissions().iterator().next();
        assertThat(returnedSubmission.submitted()).as("Latest submission is present").isTrue();
        assertThat(returnedSubmission.submissionExerciseType()).as("Submission reports its kind so the client can discriminate it").isEqualTo("text");
        assertThat(returnedSubmission.results()).as("Latest result is present").hasSize(1);
        var returnedResult = returnedSubmission.results().getFirst();
        // The assessment button of the participation table reads the completion date of the latest result.
        assertThat(returnedResult.completionDate()).as("The result reports when it was completed").isNotNull();
        assertThat(returnedResult.score()).as("The result reports its score").isEqualTo(100D);

        // Submission and Result should not be present for a Team of which the user is not (!) the Team Owner
        submission = ParticipationFactory.generateTextSubmission(submissionText, Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, submission, team2b.getId(), TEST_PREFIX + "tutor2");

        CourseWithTeamExercisesDTO course4 = request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team2a), HttpStatus.OK, CourseWithTeamExercisesDTO.class);
        participation = participationsOf(course4.exercises().stream().filter(exercise -> exercise.id().equals(textExercise.getId())).findAny().orElseThrow()).getFirst();
        assertThat(participation.submissions()).as("Latest submission is not present").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseWithExercisesAndParticipationsForTeam_AsStudentInTeam_Allowed() throws Exception {
        Team team = teamRepo.save(new Team().name(TEST_PREFIX + "Team").shortName(TEST_PREFIX + "team").exercise(exercise)
                .students(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").map(Set::of).orElseThrow()));
        request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team), HttpStatus.OK, CourseWithTeamExercisesDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseWithExercisesAndParticipationsForTeam_AsStudentNotInTeam_Forbidden() throws Exception {
        Team team = teamUtilService.addTeamsForExercise(exercise, TEST_PREFIX + "team_forb", TEST_PREFIX + "otherStudent", 1, tutor).getFirst();
        request.get(resourceUrlCourseWithExercisesAndParticipationsForTeam(course, team), HttpStatus.FORBIDDEN, CourseWithTeamExercisesDTO.class);
    }
}
