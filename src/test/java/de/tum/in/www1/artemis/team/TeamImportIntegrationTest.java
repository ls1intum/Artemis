package de.tum.in.www1.artemis.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.TeamImportStrategyType;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class TeamImportIntegrationTest extends AbstractSpringIntegrationIndependentTest {

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
    private CourseUtilService courseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    private enum ImportType {
        FROM_EXERCISE, FROM_LIST
    }

    private Course course;

    private Exercise sourceExercise;

    private Exercise destinationExercise;

    private List<Team> importedTeams;

    private List<Team> importedTeamsBody;

    private User tutor;

    private static final String TEST_PREFIX = "tiitest";

    private static final String REGISTRATION_NUMBER_PREFIX = "tii";

    private String fromExerciseEndpointUrl() {
        return "/api/exercises/" + destinationExercise.getId() + "/teams/import-from-exercise/";
    }

    private String importFromExerciseUrl(Exercise exercise, TeamImportStrategyType importStrategyType) {
        return fromExerciseEndpointUrl() + exercise.getId() + "?importStrategyType=" + importStrategyType;
    }

    private String importFromExerciseUrl(Exercise exercise) {
        return fromExerciseEndpointUrl() + exercise.getId() + "?importStrategyType=" + TeamImportStrategyType.CREATE_ONLY;
    }

    private String importFromSourceExerciseUrl(TeamImportStrategyType importStrategyType) {
        return importFromExerciseUrl(sourceExercise, importStrategyType);
    }

    private String importFromSourceExerciseUrl() {
        return importFromSourceExerciseUrl(TeamImportStrategyType.CREATE_ONLY);
    }

    private String fromListEndpointUrl() {
        return "/api/exercises/" + destinationExercise.getId() + "/teams/import-from-list";
    }

    private String importFromListUrl(TeamImportStrategyType importStrategyType) {
        return fromListEndpointUrl() + "?importStrategyType=" + importStrategyType;
    }

    private String importFromListUrl() {
        return fromListEndpointUrl() + "?importStrategyType=" + TeamImportStrategyType.CREATE_ONLY;
    }

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 1, 0, 1);
        course = courseUtilService.addCourseWithModelingAndTextExercise();

        // Make both source and destination exercise team exercises
        sourceExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "Modeling");
        sourceExercise.setMode(ExerciseMode.TEAM);
        sourceExercise = exerciseRepo.save(sourceExercise);
        destinationExercise = exerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
        destinationExercise.setMode(ExerciseMode.TEAM);
        destinationExercise = exerciseRepo.save(destinationExercise);
        Pair<List<Team>, List<Team>> importedTeamsWithBody = getImportedTeamsAndBody("import", TEST_PREFIX + "student", REGISTRATION_NUMBER_PREFIX + "R");
        importedTeams = importedTeamsWithBody.getFirst();
        importedTeamsBody = importedTeamsWithBody.getSecond();
        // Select a tutor for the teams
        tutor = userRepo.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
    }

    private void testImportTeamsIntoExercise(ImportType type, TeamImportStrategyType importStrategyType, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType importStrategy = TeamImportStrategyType.CREATE_ONLY;
        if (importStrategyType != null) {
            importStrategy = importStrategyType;
        }
        String url = importFromSourceExerciseUrl(importStrategy);
        if (type == ImportType.FROM_LIST) {
            url = importFromListUrl(importStrategy);
        }
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(url, body, Team.class, HttpStatus.OK);
        assertCorrectnessOfImport(addedTeams, destinationTeamsAfter);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(TeamImportStrategyType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseIntoEmptyExercise(TeamImportStrategyType importStrategyType) throws Exception {
        List<Team> sourceTeams = teamUtilService.addTeamsForExercise(sourceExercise, 3, tutor);
        testImportTeamsIntoExercise(ImportType.FROM_EXERCISE, importStrategyType, null, sourceTeams);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(TeamImportStrategyType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromListIntoEmptyExercise(TeamImportStrategyType importStrategyType) throws Exception {
        testImportTeamsIntoExercise(ImportType.FROM_LIST, importStrategyType, importedTeamsBody, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy(ImportType type, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.PURGE_EXISTING;
        teamUtilService.addTeamsForExercise(destinationExercise, 4, tutor);
        testImportTeamsIntoExercise(type, strategyType, body, addedTeams);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseIntoExerciseWithNoConflictsUsingPurgeExistingStrategy() throws Exception {
        List<Team> sourceTeams = teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "sT", 2, tutor);
        testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy(ImportType.FROM_EXERCISE, null, sourceTeams);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromListWithNoConflictsUsingPurgeExistingStrategy() throws Exception {
        testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy(ImportType.FROM_LIST, importedTeamsBody, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy(ImportType type, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> destinationTeamsBefore = teamUtilService.addTeamsForExercise(destinationExercise, 1, tutor);
        // destination teams before + source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addLists(destinationTeamsBefore, addedTeams));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseIntoExerciseWithNoConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> sourceTeams = teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "sourceTeam", 3, tutor);
        // destination teams before + source teams = destination teams after
        testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy(ImportType.FROM_EXERCISE, null, sourceTeams);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromListIntoExerciseWithNoConflictsUsingCreateOnlyStrategy() throws Exception {
        // destination teams before + imported teams = destination teams after
        testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy(ImportType.FROM_LIST, importedTeamsBody, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy(ImportType type, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.PURGE_EXISTING;
        teamUtilService.addTeamsForExercise(destinationExercise, TEST_PREFIX + "ssn", 2, tutor);
        // imported source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addedTeams);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseIntoExerciseWithConflictsUsingPurgeExistingStrategy() throws Exception {
        List<Team> sourceTeams = teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "ssn", TEST_PREFIX + "o", 3, tutor);
        testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy(ImportType.FROM_EXERCISE, null, sourceTeams);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportFromListIntoExerciseWithConflictsUsingPurgeExistingStrategy() throws Exception {
        Pair<List<Team>, List<Team>> importedTeamsWithBody = getImportedTeamsAndBody(TEST_PREFIX + "ssn", TEST_PREFIX + "o", REGISTRATION_NUMBER_PREFIX + "O");
        importedTeams = importedTeamsWithBody.getFirst();
        importedTeamsBody = importedTeamsWithBody.getSecond();
        testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy(ImportType.FROM_LIST, importedTeamsBody, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy(ImportType type, List<Team> body, List<Team> teamsWithoutConflict) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> destinationTeamsBefore = teamUtilService.addTeamsForExercise(destinationExercise, TEST_PREFIX + "ssn", 3, tutor);
        // destination teams before + conflict-free source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addLists(destinationTeamsBefore, teamsWithoutConflict));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> sourceTeamsWithoutConflict = teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "st", 1, tutor);
        teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "ssn", TEST_PREFIX + "o", 2, tutor);
        testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy(ImportType.FROM_EXERCISE, null, sourceTeamsWithoutConflict);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportFromListIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy() throws Exception {
        Pair<List<Team>, List<Team>> importedTeamsWithConflictAndBody = getImportedTeamsAndBody(TEST_PREFIX + "ssn", TEST_PREFIX + "o", "O");
        List<Team> importedTeamsWithConflictBody = importedTeamsWithConflictAndBody.getSecond();
        testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy(ImportType.FROM_LIST, addLists(importedTeamsWithConflictBody, importedTeamsBody),
                importedTeams);
    }

    private void testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy(ImportType type, List<Team> body, List<Team> teamsWithoutConflict,
            List<Team> teamsWithConflicts) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> destinationTeamsBefore = teamRepo
                .saveAll(teamsWithConflicts.stream().map(team -> team.exercise(destinationExercise).shortName(TEST_PREFIX + "other" + team.getShortName())).toList());
        // destination teams before + conflict-free source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addLists(destinationTeamsBefore, teamsWithoutConflict));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> sourceTeamsWithoutConflict = teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "sto", 1, tutor);
        List<Team> sourceTeamsWithStudentConflict = teamUtilService.addTeamsForExercise(sourceExercise, TEST_PREFIX + "st", 3, tutor);
        // destination teams before + conflict-free source teams = destination teams after
        testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy(ImportType.FROM_EXERCISE, null, sourceTeamsWithoutConflict, sourceTeamsWithStudentConflict);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromListIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy() throws Exception {
        Pair<List<Team>, List<Team>> importedTeamsWithStudentConflictAndBody = getImportedTeamsAndBody(TEST_PREFIX + "wc", TEST_PREFIX + "im", REGISTRATION_NUMBER_PREFIX + "X");
        List<Team> importedTeamsWithStudentConflict = importedTeamsWithStudentConflictAndBody.getFirst();
        List<Team> importedTeamsWithStudentConflictBody = importedTeamsWithStudentConflictAndBody.getSecond();
        // destination teams before + conflict-free imported teams = destination teams after
        testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy(ImportType.FROM_LIST, addLists(importedTeamsWithStudentConflictBody, importedTeamsBody),
                importedTeams, importedTeamsWithStudentConflict);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseBadRequests() throws Exception {
        // Specifying the destination exercise to also be the source exercise should fail
        request.put(importFromExerciseUrl(destinationExercise), null, HttpStatus.BAD_REQUEST);

        // If the destination exercise is not a team exercise, the request should fail
        destinationExercise.setMode(ExerciseMode.INDIVIDUAL);
        exerciseRepo.save(destinationExercise);
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.BAD_REQUEST);

        destinationExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(destinationExercise);
        // If the source exercise is not a team exercise, the request should fail
        sourceExercise.setMode(ExerciseMode.INDIVIDUAL);
        exerciseRepo.save(sourceExercise);
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromListBadRequests() throws Exception {
        // If the destination exercise is not a team exercise, the request should fail
        destinationExercise.setMode(ExerciseMode.INDIVIDUAL);
        exerciseRepo.save(destinationExercise);
        request.put(importFromListUrl(), importedTeamsBody, HttpStatus.BAD_REQUEST);
        destinationExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(destinationExercise);

        // If user with given registration number does not exist, the request should fail
        List<Team> teams = new ArrayList<>();
        Team team = TeamFactory.generateTeamForExercise(destinationExercise, TEST_PREFIX + "failTeam", TEST_PREFIX + "fail", 1, null);
        // If students not added with user repo then they do not exist so it should fail
        teams.add(team);
        request.put(importFromListUrl(), getTeamsIntoRegistrationNumberOnlyTeams(teams), HttpStatus.BAD_REQUEST);

        // If user with given login does not exist, the request should fail
        request.put(importFromListUrl(), getTeamsIntoLoginOnlyTeams(teams), HttpStatus.BAD_REQUEST);

        // If user does not have an identifier: registration number or login, the request should fail
        userRepo.saveAll(teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList());
        request.put(importFromListUrl(), getTeamsIntoOneIdentifierTeams(teams, null), HttpStatus.BAD_REQUEST);

        // If user's registration number points to same user with a login in request, it should fail
        request.put(importFromListUrl(), addLists(getTeamsIntoLoginOnlyTeams(teams), getTeamsIntoRegistrationNumberOnlyTeams(teams)), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testImportTeamsFromExerciseForbiddenAsTutor() throws Exception {
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testImportTeamsFromListForbiddenAsTutor() throws Exception {
        request.put(importFromListUrl(), importedTeamsBody, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromExerciseForbiddenAsInstructorOfOtherCourse() throws Exception {
        // If the instructor is not part of the correct course instructor group anymore, he should not be able to import teams
        course.setInstructorGroupName("Different group name");
        courseRepo.save(course);

        request.put(importFromSourceExerciseUrl(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTeamsFromListForbiddenAsInstructorOfOtherCourse() throws Exception {
        // If the instructor is not part of the correct course instructor group anymore, he should not be able to import teams
        course.setInstructorGroupName("Different group name");
        courseRepo.save(course);

        request.put(importFromListUrl(), importedTeamsBody, HttpStatus.FORBIDDEN);
    }

    /**
     * Verifies that the teams including their students were correctly imported in the destination exercise according to the expectations
     *
     * @param expectedTeamsAfterImport List of teams that are expected to be in the destination exercise now after the import
     * @param actualTeamsAfterImport   List of teams that are actually in the destination exercise after the import according to response
     */
    private void assertCorrectnessOfImport(List<Team> expectedTeamsAfterImport, List<Team> actualTeamsAfterImport) {
        List<Team> destinationTeamsInDatabase = teamRepo.findAllByExerciseId(destinationExercise.getId());
        assertThat(actualTeamsAfterImport).as("Imported teams were persisted into destination exercise.").isEqualTo(destinationTeamsInDatabase);

        assertThat(actualTeamsAfterImport).as("Teams were correctly imported.").usingRecursiveComparison().ignoringFields("id", "exercise", "createdDate", "lastModifiedDate")
                .usingOverriddenEquals().ignoringOverriddenEqualsForTypes(Team.class).ignoringCollectionOrder().isEqualTo(expectedTeamsAfterImport);
    }

    static <T> List<T> addLists(List<T> a, List<T> b) {
        return Stream.of(a, b).flatMap(Collection::stream).toList();
    }

    private Pair<List<Team>, List<Team>> getImportedTeamsAndBody(String shortNamePrefix, String loginPrefix, String registrationPrefix) {
        List<Team> generatedTeams = teamUtilService.generateTeamsForExercise(destinationExercise, shortNamePrefix, loginPrefix, 3, null, TEST_PREFIX + "instructor1",
                registrationPrefix);
        var users = generatedTeams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(u -> userUtilService.cleanUpRegistrationNumberForUser(u));
        userRepo.saveAll(users);
        List<Team> teamsWithLogins = getTeamsIntoLoginOnlyTeams(generatedTeams.subList(0, 2));
        List<Team> teamsWithRegistrationNumbers = getTeamsIntoRegistrationNumberOnlyTeams(generatedTeams.subList(2, 3));
        List<Team> body = Stream.concat(teamsWithLogins.stream(), teamsWithRegistrationNumbers.stream()).toList();
        return Pair.of(generatedTeams, body);
    }

    private List<Team> getTeamsIntoLoginOnlyTeams(List<Team> teams) {
        return getTeamsIntoOneIdentifierTeams(teams, TEST_PREFIX + "login");
    }

    private List<Team> getTeamsIntoRegistrationNumberOnlyTeams(List<Team> teams) {
        return getTeamsIntoOneIdentifierTeams(teams, TEST_PREFIX + "registrationNumber");
    }

    private List<Team> getTeamsIntoOneIdentifierTeams(List<Team> teams, String identifier) {
        return teams.stream().map(team -> {
            Team newTeam = new Team();
            newTeam.setName(team.getName());
            newTeam.setShortName(team.getShortName());
            newTeam.setOwner(team.getOwner());
            List<User> newStudents = team.getStudents().stream().map(student -> {
                User newStudent = new User();
                newStudent.setFirstName(student.getFirstName());
                newStudent.setLastName(student.getLastName());
                if ((TEST_PREFIX + "login").equals(identifier)) {
                    newStudent.setLogin(student.getLogin());
                }
                else if ((TEST_PREFIX + "registrationNumber").equals(identifier)) {
                    newStudent.setVisibleRegistrationNumber(student.getRegistrationNumber());
                }
                return newStudent;
            }).toList();
            newTeam.setStudents(new HashSet<>(newStudents));
            return newTeam;
        }).toList();
    }
}
